
package org.elasticsearch.apps.support;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;

/**
 * Format java exception messages and stack traces.
 *
 * @author <a href="mailto:joergprante@gmail.com;rg Prante</a>
 */
public final class ExceptionFormatter {

    private ExceptionFormatter() {
    }

    public static void append(StringBuilder buf, Throwable t,
            int level, boolean details) {
        try {
            if (((t != null) && (t.getMessage() != null))
                    && (t.getMessage().length() > 0)) {
                if (details && (level > 0)) {
                    buf.append("\n\nCaused by\n");
                }
                buf.append(t.getMessage());
            }
            if (details) {
                if (t != null) {
                    if ((t.getMessage() != null)
                            && (t.getMessage().length() == 0)) {
                        buf.append("\n\nCaused by ");
                    } else {
                        buf.append("\n\n");
                    }
                }
                StringWriter sw = new StringWriter();
                if (t != null) {
                    t.printStackTrace(new PrintWriter(sw));
                }
                buf.append(sw.toString());
            }
            if (t != null) {
                Method method = t.getClass().getMethod("getCause",
                        new Class[]{});
                Throwable cause = (Throwable) method.invoke(t,
                        (Object) null);
                if (cause != null) {
                    append(buf, cause, level + 1, details);
                }
            }
        } catch (Exception ex) {
        }
    }

    /**
     * Format exception with stack trace
     *
     * @param t the thrown object
     *
     * @return the formatted exception
     */
    public static String format(Throwable t) {
        StringBuilder sb = new StringBuilder();
        append(sb, t, 0, true);
        return sb.toString();
    }
}
