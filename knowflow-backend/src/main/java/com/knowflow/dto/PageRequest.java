package com.knowflow.dto;

public final class PageRequest {

    private PageRequest() {
    }

    public static long normalizePage(Long page) {
        return page == null || page < 1 ? 1 : page;
    }

    public static long normalizeSize(Long size) {
        if (size == null || size < 1) {
            return 20;
        }
        return Math.min(size, 100);
    }
}
