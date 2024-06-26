package mage.cards.decks.importer;

import mage.cards.decks.DeckCardLists;
import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Paths;

public class CodDeckImportTest {

    private static final FakeCardLookup LOOKUP = new FakeCardLookup(false)
            .addCard("Forest")
            .addCard("Razorverge Thicket")
            .addCard("Avacyn's Pilgrim")
            .addCard("War Priest of Thune");

    @Test
    public void testImport() {
        CodDeckImporter importer = new CodDeckImporter() {
            @Override
            public CardLookup getCardLookup() {
                return LOOKUP;
            }
        };
        StringBuilder errors = new StringBuilder();
        DeckCardLists deck = importer.importDeck(
                Paths.get("src", "test", "data", "importer", "testdeck.cod").toString(),
                errors,
                false
        );
        Assert.assertEquals("Could not find card: '@#$NOT A REAL CARD NAME@#$'\n", errors.toString());

        Assert.assertEquals("Deck Name", deck.getName());
        TestDeckChecker.checker()
                .addMain("Forest", 12)
                .addMain("Razorverge Thicket", 5)
                .addMain("Avacyn's Pilgrim", 1)
                .addSide("War Priest of Thune", 3)
                .verify(deck, 18, 3);
    }
}
