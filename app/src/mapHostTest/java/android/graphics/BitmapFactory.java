package android.graphics;

import java.io.File;

public final class BitmapFactory {
    private BitmapFactory() { }
    public static Bitmap decodeFile(String path) {
        return new File(path).length() > 0 ? new Bitmap() : null;
    }
    public static Bitmap decodeByteArray(byte[] bytes, int offset, int length) {
        return length > 0 ? new Bitmap() : null;
    }
}
