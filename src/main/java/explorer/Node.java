package explorer;

import java.util.HashMap;
import java.util.TreeMap;

/**
 * A node representing a unique combination of letters and possibly one or more anagrams,
 * each of which is a valid dictionary word.
 *
 */

public class Node {

    final HashMap<String, String> definitions = new HashMap<>(2);

    final TreeMap<Character, Node> children = new TreeMap<>();

}