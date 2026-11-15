/*
 * Copyright 2016 Hippo Seven
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

package com.hippo.widget;

/**
 * 占位类型。
 *
 * EhViewer 的 PreviewSet 系列把 {@code LoadImageView}（一个 Android View）当作"图片槽位"的
 * 参数类型使用。Compose 侧不会走这些 setPreviewAt 路径（我们直接用 URL 渲染），
 * 所以这里只提供签名所需的方法，全部空实现。
 *
 * 不要在这个类里加任何真实行为。
 */
public class LoadImageView {

    public void load(long key, String url) {
    }

    public void load(String key, String url) {
    }

    public void resetClip() {
    }

    public void setClip(int offsetX, int offsetY, int width, int height) {
    }
}
