package ai.nixiesearch.util;

import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;

public class PrintLoggerFactory implements ILoggerFactory {
    @Override
    public Logger getLogger(String name) {
        return new ai.nixiesearch.util.PrintLogger(name);
    }
}