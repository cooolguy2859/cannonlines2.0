package dev.cannonlines;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Optional Mod Control Center bridge for the CannonLines 2.x project. */
public final class ControlCenterBridge {
    public void registerFeatures(Object registry) {
        Object client = FabricLoader.getInstance().getEntrypoints("client", ClientModInitializer.class)
                .stream().filter(value -> value.getClass().getName().equals("dev.cannonlines.CannonLinesClient"))
                .findFirst().orElse(null);
        if (client == null) return;
        try {
            Field field = client.getClass().getDeclaredField("enabled");
            field.setAccessible(true);
            BooleanSupplier getter = () -> {
                try { return field.getBoolean(client); }
                catch (ReflectiveOperationException e) { return false; }
            };
            Consumer<Boolean> setter = value -> {
                try { field.setBoolean(client, value); }
                catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
            };
            Method register = Class.forName("dev.mcgee.controlcenter.ModControlCenterClient")
                    .getMethod("register", Object.class, String.class, String.class, String.class,
                            BooleanSupplier.class, Consumer.class);
            register.invoke(null, registry, "CannonLines 2.x", "Particle trail",
                    "Turns the slime particle trail on or off.", getter, setter);
        } catch (ClassNotFoundException ignored) {
            // The control panel is optional.
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not register CannonLines control", e);
        }
    }
}
