package lab2;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.CyclicBarrier;

/** Mini-jcstress, store buffering:  T1: x=1; r1=y    T2: y=1; r2=x
 *  (0,0) means each thread missed the other's write — forbidden under seq_cst.
 *  Настройки запуска находятся в Main.java. */
public final class MiniStress {
    static final VarHandle INT = MethodHandles.arrayElementVarHandle(int[].class);
    static int[] x, y, r1, r2;

    static void actor1(String mode, int n) {
        switch (mode) {
            case "plain"    -> { for (int i = 0; i < n; i++) { x[i] = 1; r1[i] = y[i]; } }
            case "acqrel"   -> { for (int i = 0; i < n; i++) { INT.setRelease(x, i, 1); r1[i] = (int) INT.getAcquire(y, i); } }
            default         -> { for (int i = 0; i < n; i++) { INT.setVolatile(x, i, 1); r1[i] = (int) INT.getVolatile(y, i); } }
        }
    }

    static void actor2(String mode, int n) {
        switch (mode) {
            case "plain"    -> { for (int i = 0; i < n; i++) { y[i] = 1; r2[i] = x[i]; } }
            case "acqrel"   -> { for (int i = 0; i < n; i++) { INT.setRelease(y, i, 1); r2[i] = (int) INT.getAcquire(x, i); } }
            default         -> { for (int i = 0; i < n; i++) { INT.setVolatile(y, i, 1); r2[i] = (int) INT.getVolatile(x, i); } }
        }
    }

    public static void main(String[] a) throws Exception {
        Main.requireNoArguments(a);
        Main.runMiniStress();
    }

    public static void run(String mode, int rounds, int batchSize) throws Exception {
        if (!java.util.Set.of("plain", "acqrel", "volatile", "naive").contains(mode))
            throw new IllegalArgumentException("unknown memory mode " + mode);
        if (rounds <= 0 || batchSize <= 0)
            throw new IllegalArgumentException("MEMORY_ROUNDS and MEMORY_CELLS must be positive");
        boolean naive = mode.equals("naive");               // a barrier around every single pair
        int cells = naive ? 1 : batchSize;
        String m = naive ? "plain" : mode;
        int roundsTotal = naive ? Math.multiplyExact(rounds, batchSize) : rounds;
        x = new int[cells]; y = new int[cells]; r1 = new int[cells]; r2 = new int[cells];
        long[] hist = new long[4];                          // index = r1*2 + r2
        CyclicBarrier go = new CyclicBarrier(3), done = new CyclicBarrier(3);
        Thread t1 = new Thread(() -> loop(go, done, roundsTotal, () -> actor1(m, cells)));
        Thread t2 = new Thread(() -> loop(go, done, roundsTotal, () -> actor2(m, cells)));
        t1.start(); t2.start();
        for (int r = 0; r < roundsTotal; r++) {
            go.await();
            done.await();
            for (int i = 0; i < cells; i++) { hist[r1[i] * 2 + r2[i]]++; x[i] = y[i] = 0; }
        }
        t1.join(); t2.join();
        long total = hist[0] + hist[1] + hist[2] + hist[3];
        System.out.printf("mode=%s samples=%,d%n", mode, total);
        String[] names = {"(0,0)", "(0,1)", "(1,0)", "(1,1)"};
        for (int k = 0; k < 4; k++)
            System.out.printf("  %s %,14d  %7.3f%%%s%n", names[k], hist[k], 100.0 * hist[k] / total, k == 0 ? "  forbidden under seq_cst" : "");
    }

    static void loop(CyclicBarrier go, CyclicBarrier done, int rounds, Runnable body) {
        try { for (int r = 0; r < rounds; r++) { go.await(); body.run(); done.await(); } }
        catch (Exception e) { throw new RuntimeException(e); }
    }
}
