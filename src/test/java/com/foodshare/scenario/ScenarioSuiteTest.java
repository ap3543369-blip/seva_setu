package com.foodshare.scenario;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Runs the end-to-end scenarios (see ScenarioSuite). The EcoAI forecast checks need Python + ecoai/requirements.txt; without them they are skipped. */
class ScenarioSuiteTest {
    @Test
    void everyScenarioPasses() throws Exception {
        ScenarioSuite.Result r = ScenarioSuite.run();
        assertEquals(0, r.failed(), "Failed checks:\n" + String.join("\n", r.failures()));
    }
}
