package com.github.javahello.proxy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class TestHelper {
    public static String readClasspathFile(String filepath) throws IOException {
        try (InputStream in = TestHelper.class.getResourceAsStream(filepath)) {
            Objects.requireNonNull(in, filepath + " 不存在");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
