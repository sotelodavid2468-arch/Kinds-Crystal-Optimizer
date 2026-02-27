package com.kinds.kindscrystaloptimizer.util;

import com.mojang.logging.LogUtils;

public class Logger {

    private static final String PREFIX = "[KindsCrystalOptimizer] ";
    private final org.slf4j.Logger logger = LogUtils.getLogger();

    public void info(String message) {
        logger.info("{}{}", PREFIX, message);
    }

    public void warn(String message) {
        logger.warn("{}{}", PREFIX, message);
    }

    public void error(String message) {
        logger.error("{}{}", PREFIX, message);
    }

    public void debug(String message) {
        logger.debug("{}{}", PREFIX, message);
    }
}
