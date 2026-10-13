package org.labs.collectors;

public class NoRecheckDoubleBufferCollector extends DoubleBufferCollector {

    @Override
    int enter(ThreadBuffers my) {
        int b = active;
        my.inside.set(b);
        return b;
    }

}
