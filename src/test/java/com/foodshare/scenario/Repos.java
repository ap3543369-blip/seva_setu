package com.foodshare.scenario;

import java.lang.reflect.*;
import java.util.*;
import java.util.regex.*;

/** In-memory stand-in for Spring Data repositories. It also interprets derived query names (findByXAndY...OrderBy...) the way Spring Data does,
 *  so a wrong property name in a repository method fails here with the same kind of error Spring would give at startup. */
public class Repos {
    public static <R> R create(Class<R> iface) {
        Class<?> entity = (Class<?>) ((ParameterizedType) iface.getGenericInterfaces()[0]).getActualTypeArguments()[0];
        return iface.cast(Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[]{iface}, new Store(iface, entity)));
    }

    /** Fake for a hand-written @Query method (these cannot be derived from the name). */
    public static void custom(Object repo, String method, java.util.function.Function<Object[], Object> f) {
        ((Store) Proxy.getInvocationHandler(repo)).custom.put(method, f);
    }

    static final Pattern NAME = Pattern.compile("^(find|count|exists|delete)((?:All|Top\\d+|First\\d*|Distinct)*)By(.*)$");

    static class Store implements InvocationHandler {
        final Class<?> iface, entity;
        final Map<Object, Object> rows = new LinkedHashMap<>();
        long seq = 0;
        final Map<String, java.util.function.Function<Object[], Object>> custom = new HashMap<>();
        final Field idField;

        Store(Class<?> iface, Class<?> entity) {
            this.iface = iface;
            this.entity = entity;
            Field f = null;
            for (Field x : entity.getDeclaredFields()) if (x.isAnnotationPresent(jakarta.persistence.Id.class)) f = x;
            if (f == null) throw new IllegalStateException("no @Id on " + entity.getSimpleName());
            f.setAccessible(true);
            idField = f;
        }

        @SuppressWarnings("unchecked")
        public Object invoke(Object proxy, Method m, Object[] a) throws Throwable {
            switch (m.getName()) {
                case "save": return save(a[0]);
                case "saveAll": { List<Object> out = new ArrayList<>(); for (Object o : (Iterable<Object>) a[0]) out.add(save(o)); return out; }
                case "findById": return Optional.ofNullable(rows.get(a[0]));
                case "findAll": return new ArrayList<>(rows.values());
                case "count": return (long) rows.size();
                case "existsById": return rows.containsKey(a[0]);
                case "delete": rows.values().remove(a[0]); return null;
                case "deleteById": rows.remove(a[0]); return null;
                case "deleteAll": rows.clear(); return null;
                case "flush": return null;
                case "toString": return iface.getSimpleName() + rows.keySet();
                case "hashCode": return System.identityHashCode(proxy);
                case "equals": return proxy == a[0];
                default: {
                    Object[] args = a == null ? new Object[0] : a;
                    if (custom.containsKey(m.getName())) return custom.get(m.getName()).apply(args);
                    return derived(m, args);
                }
            }
        }

        Object save(Object e) throws Exception {
            Object id = idField.get(e);
            if (id == null) {
                if (idField.getType() != Long.class) throw new IllegalStateException("cannot generate id for " + idField.getType());
                id = ++seq;
                idField.set(e, id);
            }
            rows.put(id, e);
            return e;
        }

        Object derived(Method m, Object[] args) throws Exception {
            Matcher mt = NAME.matcher(m.getName());
            if (!mt.matches()) throw new UnsupportedOperationException("not a derived query (custom @Query?): " + iface.getSimpleName() + "." + m.getName());
            String kind = mt.group(1), flags = mt.group(2), rest = mt.group(3);
            Matcher top = Pattern.compile("Top(\\d+)").matcher(flags);
            int limit = top.find() ? Integer.parseInt(top.group(1)) : Integer.MAX_VALUE;
            String[] split = rest.split("OrderBy", 2);
            List<String> conds = split[0].isEmpty() ? List.of() : Arrays.asList(split[0].split("And"));
            List<Object> found = new ArrayList<>();
            for (Object row : rows.values()) {
                int ai = 0;
                boolean ok = true;
                for (String c : conds) {
                    boolean ic = false, needsArg = true;
                    Object expect = null;
                    String prop = c;
                    if (prop.endsWith("IgnoreCase")) { ic = true; prop = prop.substring(0, prop.length() - 10); }
                    boolean isTrue = false, isFalse = false;
                    if (prop.endsWith("True")) { isTrue = true; needsArg = false; prop = prop.substring(0, prop.length() - 4); }
                    else if (prop.endsWith("False")) { isFalse = true; needsArg = false; prop = prop.substring(0, prop.length() - 5); }
                    if (needsArg) expect = args[ai++];
                    Object actual = prop(row, prop);
                    boolean match = isTrue ? Boolean.TRUE.equals(actual) : isFalse ? Boolean.FALSE.equals(actual) : same(actual, expect, ic);
                    if (!match) { ok = false; break; }
                }
                if (ok) found.add(row);
            }
            if (split.length > 1 && !split[1].isEmpty()) {
                List<String[]> keys = new ArrayList<>();
                Matcher om = Pattern.compile("(.+?)(Asc|Desc)").matcher(split[1]);
                while (om.find()) keys.add(new String[]{om.group(1), om.group(2)});
                for (String[] k : keys) prop(entityProbe(), k[0]); // fails when the property does not exist
                Comparator<Object> cmp = null;
                for (String[] k : keys) {
                    Comparator<Object> c = Comparator.comparing(o -> (Comparable) uncheck(() -> prop(o, k[0])), Comparator.nullsLast(Comparator.naturalOrder()));
                    if (k[1].equals("Desc")) c = c.reversed();
                    cmp = cmp == null ? c : cmp.thenComparing(c);
                }
                if (cmp != null) found.sort(cmp);
            }
            if (found.size() > limit) found = new ArrayList<>(found.subList(0, limit));
            Class<?> rt = m.getReturnType();
            if (kind.equals("count")) return rt == int.class ? (Object) found.size() : (Object) (long) found.size();
            if (kind.equals("exists")) return !found.isEmpty();
            if (kind.equals("delete")) { rows.values().removeAll(found); return rt == long.class ? (Object) (long) found.size() : null; }
            if (rt == Optional.class) return found.stream().findFirst();
            return found;
        }

        Object probe;
        Object entityProbe() throws Exception {
            if (probe == null) { Constructor<?> c = entity.getDeclaredConstructor(); c.setAccessible(true); probe = c.newInstance(); }
            return probe;
        }
    }

    interface Thrower<T> { T get() throws Exception; }
    static <T> T uncheck(Thrower<T> t) { try { return t.get(); } catch (Exception e) { throw new RuntimeException(e); } }

    static Method find(Class<?> c, String cap) {
        for (String pre : new String[]{"get", "is"}) {
            try { return c.getMethod(pre + cap); } catch (NoSuchMethodException ignored) { }
        }
        return null;
    }

    static Object prop(Object o, String name) throws Exception {
        if (o == null) return null;
        Method m = find(o.getClass(), name);
        if (m != null) return m.invoke(o);
        for (int i = 1; i < name.length(); i++) {
            if (Character.isUpperCase(name.charAt(i))) {
                Method h = find(o.getClass(), name.substring(0, i));
                if (h != null) return prop(h.invoke(o), name.substring(i));
            }
        }
        throw new IllegalStateException("No property '" + name + "' found for type " + o.getClass().getSimpleName());
    }

    static boolean same(Object a, Object b, boolean ignoreCase) throws Exception {
        if (a == null || b == null) return a == b;
        if (ignoreCase && a instanceof String s && b instanceof String t) return s.equalsIgnoreCase(t);
        Method ia = find(a.getClass(), "Id"), ib = find(b.getClass(), "Id");
        if (ia != null && ib != null && a.getClass() == b.getClass()) return Objects.equals(ia.invoke(a), ib.invoke(b));
        return a.equals(b);
    }
}
