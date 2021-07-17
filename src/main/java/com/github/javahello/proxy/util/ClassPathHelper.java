/*
 * Copyright 2021 kailuo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.javahello.proxy.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * @author kailuo
 */
public abstract class ClassPathHelper {
    public static String readClasspathFile(String filepath) throws IOException {
        try (InputStream in = ClassPathHelper.class.getResourceAsStream(filepath)) {
            Objects.requireNonNull(in, filepath + " 不存在");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
