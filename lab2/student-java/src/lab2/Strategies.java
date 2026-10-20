package lab2;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/* Стратегии выбора следующего потока.
 * FirstAlive — этап 1, Replay — этап 2, RandomPick — этап 3, Dfs — этап 4 (ограничение bound — этап 5).
 * enabled — номера потоков, которые могут сделать шаг, по возрастанию; last — прошлый выбранный (-1 в начале). */
public final class Strategies {
    private Strategies() { }

    /** Всегда первый поток из enabled. */
    public static final class FirstAlive implements Strategy {
        public int pick(List<Integer> enabled, int last) {
            return enabled.get(0);
        }
    }

    /** Проигрывает трассу: на i-м решении выбирает поток trace[i] ('A' -> 0, 'B' -> 1). */
    public static final class Replay implements Strategy {
        private final String trace;
        private int pos;

        public Replay(String trace) { this.trace = trace; }

        /** Сбросить позицию в трассе перед прогоном. */
        public void beginRun() { pos = 0; }

        public int pick(List<Integer> enabled, int last) {
            if (pos >= trace.length())
                throw new IllegalStateException("trace " + trace + " ended after " + pos + " decisions, but the run goes on");
            char c = trace.charAt(pos);
            int t = c - 'A';
            if (!enabled.contains(t))
                throw new IllegalStateException("trace " + trace + ": decision " + (pos + 1) + " wants thread " + c
                        + ", but it is not enabled (enabled=" + letters(enabled) + ")");
            pos++;
            return t;
        }
    }

    /** Случайный поток: enabled.get(rnd.nextInt(enabled.size())), rnd = new java.util.Random(seed). */
    public static final class RandomPick implements Strategy {
        private final long seed;
        private Random rnd;

        public RandomPick(long seed) {
            this.seed = seed;
            this.rnd = new Random(seed);
        }

        public void beginRun() { rnd = new Random(seed); }

        public int pick(List<Integer> enabled, int last) {
            return enabled.get(rnd.nextInt(enabled.size()));
        }
    }

    /** Stateless DFS по дереву выборов: каждый прогон с нуля, повторяет префикс прошлого и берёт следующий вариант.
     *  bound — максимум вытеснений (этап 5); для dfs runner передаёт Integer.MAX_VALUE. */
    public static final class Dfs implements Strategy {
        /** Развилка на пути: варианты (уже отфильтрованные по bound) и номер выбранного. */
        private static final class Node {
            final List<Integer> options;
            int chosen;

            Node(List<Integer> options) { this.options = options; }
        }

        private final int bound;
        private final List<Node> path = new ArrayList<>();
        private int pos;
        private int preemptions;

        public Dfs(int bound) { this.bound = bound; }

        /** Подготовиться к новому прогону (позиция в пути, счётчик вытеснений). */
        public void beginRun() {
            pos = 0;
            preemptions = 0;
        }

        public int pick(List<Integer> enabled, int last) {
            Node node;
            if (pos < path.size()) {
                node = path.get(pos);
            } else {
                // Вытеснение — переключение с потока, который мог бы продолжить.
                // Исчерпали лимит: если last ещё может ходить, остаётся только он.
                List<Integer> options = preemptions >= bound && enabled.contains(last)
                        ? List.of(last)
                        : List.copyOf(enabled);
                node = new Node(options);
                path.add(node);
            }
            pos++;
            int t = node.options.get(node.chosen);
            if (t != last && enabled.contains(last)) preemptions++;
            return t;
        }

        /** Выбрать следующий путь; false — дерево обойдено. */
        public boolean endRun() {
            // Прогон мог кончиться раньше (deadlock): развилки дальше pos к нему не относятся.
            while (path.size() > pos) path.remove(path.size() - 1);
            while (!path.isEmpty()) {
                Node top = path.get(path.size() - 1);
                if (top.chosen + 1 < top.options.size()) {
                    top.chosen++;
                    return true;
                }
                path.remove(path.size() - 1);
            }
            return false;
        }
    }

    private static String letters(List<Integer> ids) {
        StringBuilder sb = new StringBuilder();
        for (int id : ids) sb.append((char) ('A' + id));
        return sb.toString();
    }
}
