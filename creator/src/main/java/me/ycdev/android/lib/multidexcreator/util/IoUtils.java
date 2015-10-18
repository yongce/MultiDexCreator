package me.ycdev.android.lib.multidexcreator.util;

import java.io.Closeable;
import java.io.IOException;

public class IoUtils {
    public static void close(Closeable target) {
        if (target != null) {
            try {
                target.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }

}
