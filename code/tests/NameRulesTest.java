import de.tuefteltyp.infinitechest.NameRules;

/** Runs without Minecraft, Fabric, or test libraries: javac/java NameRulesTest. */
public final class NameRulesTest {
    private static void expect(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        expect(NameRules.normalize(null).isEmpty(), "null must be invalid");
        expect(NameRules.normalize("  ").isEmpty(), "blank must be invalid");
        expect(NameRules.normalize("  Mein  Lager  ").equals("Mein Lager"), "whitespace normalize");
        expect(NameRules.normalize("Mine_2").equals("Mine_2"), "underscore allowed");
        expect(NameRules.normalize("A-B").equals("A-B"), "hyphen allowed");
        expect(NameRules.normalize("a".repeat(25)).isEmpty(), "length limit");
        expect(NameRules.normalize("bad/name").isEmpty(), "slash invalid");
        expect(NameRules.normalize("bad:thing").isEmpty(), "colon invalid");
        expect(NameRules.normalize("bad\u00a7c").isEmpty(), "format codes invalid");
        expect(NameRules.key("Mine").equals(NameRules.key("mInE")), "casefold uniqueness");
        expect(NameRules.normalize("Werkstatt 3").equals("Werkstatt 3"), "ordinary name");
        System.out.println("NameRules: 10 tests passed");
    }
}
