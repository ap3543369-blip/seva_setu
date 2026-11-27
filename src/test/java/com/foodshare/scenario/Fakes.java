package com.foodshare.scenario;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

public class Fakes {
    public static class Enc implements PasswordEncoder {
        public String encode(CharSequence raw) { return "{enc}" + raw; }
        public boolean matches(CharSequence raw, String enc) { return enc.equals("{enc}" + raw); }
    }

    /** One browser session: same HttpSession object for every request, like a cookie. */
    public static class Browser {
        public final Map<String, Object> attrs = new HashMap<>();
        public final HttpSession session = (HttpSession) Proxy.newProxyInstance(Fakes.class.getClassLoader(), new Class<?>[]{HttpSession.class}, (p, m, a) -> switch (m.getName()) {
            case "getAttribute" -> attrs.get(a[0]);
            case "setAttribute" -> { attrs.put((String) a[0], a[1]); yield null; }
            case "invalidate" -> { attrs.clear(); yield null; }
            case "getId" -> "sess";
            default -> null;
        });
        public final HttpServletRequest request = (HttpServletRequest) Proxy.newProxyInstance(Fakes.class.getClassLoader(), new Class<?>[]{HttpServletRequest.class}, (p, m, a) -> switch (m.getName()) {
            case "getSession" -> session;
            case "changeSessionId" -> "sess2";
            default -> null;
        });
    }

    public static class File implements MultipartFile {
        final byte[] bytes; final String type; final String name;
        public File(String name, String type, byte[] bytes) { this.name = name; this.type = type; this.bytes = bytes; }
        public boolean isEmpty() { return bytes.length == 0; }
        public long getSize() { return bytes.length; }
        public InputStream getInputStream() { return new ByteArrayInputStream(bytes); }
        public String getOriginalFilename() { return name; }
        public String getContentType() { return type; }
        public byte[] getBytes() { return bytes; }
        public String getName() { return "file"; }
        public void transferTo(java.io.File dest) { throw new UnsupportedOperationException(); }
    }
}
