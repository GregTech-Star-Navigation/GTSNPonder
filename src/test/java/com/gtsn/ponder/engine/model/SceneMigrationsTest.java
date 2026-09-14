package com.gtsn.ponder.engine.model;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 版本迁移机制（{@link SceneMigrations}）的外部可观察行为：给定文档与一条有序的
 * {@code N → N+1} 迁移链，断言逐级应用、版本前进、以及不支持版本 / 断链的清晰失败。
 *
 * <p>用合成迁移（synthetic）证明机制本身，与具体版本的迁移内容解耦；生产链在 v1 为空
 * （当前无历史版本），未来提升 {@link SceneFormat#CURRENT_VERSION} 时注册真实迁移即可复用。</p>
 */
class SceneMigrationsTest {

    private static JsonObject document(int formatVersion) {
        JsonObject object = JsonParser.parseString("{\"formatVersion\":" + formatVersion + "}").getAsJsonObject();
        return object;
    }

    private static SceneMigrations.Migration migration(int fromVersion, String marker) {
        return new SceneMigrations.Migration() {
            @Override
            public int fromVersion() {
                return fromVersion;
            }

            @Override
            public JsonObject apply(JsonObject document) {
                document.addProperty(marker, true);
                return document;
            }
        };
    }

    @Test
    void appliesChainInOrderUntilTargetVersion() {
        List<SceneMigrations.Migration> chain = List.of(
                migration(1, "step1to2"),
                migration(2, "step2to3"));

        JsonObject migrated = SceneMigrations.migrate(document(1), 1, 3, chain);

        assertEquals(3, migrated.get("formatVersion").getAsInt(),
                "migrated document must advance to the target version");
        assertTrue(migrated.has("step1to2") && migrated.has("step2to3"),
                "both N->N+1 migrations must be applied in order");
    }

    @Test
    void appliesNoMigrationWhenAlreadyAtTarget() {
        JsonObject original = document(SceneFormat.CURRENT_VERSION);
        boolean[] called = { false };
        List<SceneMigrations.Migration> chain = List.of(new SceneMigrations.Migration() {
            @Override
            public int fromVersion() {
                return SceneFormat.CURRENT_VERSION;
            }

            @Override
            public JsonObject apply(JsonObject document) {
                called[0] = true;
                return document;
            }
        });

        JsonObject migrated = SceneMigrations.migrate(
                original, SceneFormat.CURRENT_VERSION, SceneFormat.CURRENT_VERSION, chain);

        assertFalse(called[0], "no migration may run when the document is already at the target version");
        assertEquals(SceneFormat.CURRENT_VERSION, migrated.get("formatVersion").getAsInt());
    }

    @Test
    void rejectsNewerThanTargetWithClearError() {
        SceneFormatException exception = assertThrows(SceneFormatException.class,
                () -> SceneMigrations.migrate(document(99), 99, SceneFormat.CURRENT_VERSION, List.of()));
        assertTrue(exception.getMessage().contains("99"), exception.getMessage());
        assertTrue(exception.getMessage().contains("formatVersion"), exception.getMessage());
    }

    @Test
    void failsClearlyWhenTheChainSkipsAVersion() {
        // Only 1->2 registered, but the document is 1 and the target is 3.
        SceneFormatException exception = assertThrows(SceneFormatException.class,
                () -> SceneMigrations.migrate(document(1), 1, 3, List.of(migration(1, "step1to2"))));
        assertTrue(exception.getMessage().contains("2"), exception.getMessage());
        assertTrue(exception.getMessage().contains("3"), exception.getMessage());
    }
}
