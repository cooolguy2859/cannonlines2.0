package dev.cannonlines;

import java.lang.reflect.Method;

/** Bridges particle spawning across the 26.2 client API mapping changes. */
final class ParticleCompat {
    private ParticleCompat() {}

    static void spawn(Object level, Object particle,
                      double x, double y, double z, double dx, double dy, double dz) {
        try {
            for (Method method : level.getClass().getMethods()) {
                if (method.getName().equals("addParticle") && method.getParameterCount() == 7) {
                    method.invoke(level, particle, x, y, z, dx, dy, dz);
                    return;
                }
            }
            throw new NoSuchMethodException("ClientLevel.addParticle");
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not spawn client particle", exception);
        }
    }
}
