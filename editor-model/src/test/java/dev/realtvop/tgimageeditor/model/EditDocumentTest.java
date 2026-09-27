package dev.realtvop.tgimageeditor.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class EditDocumentTest {
    @Test
    public void newDocumentStartsWithIdentityEdits() {
        SourceImage source = new SourceImage("content://image", 4032, 3024);
        EditDocument document = EditDocument.create(source);

        assertSame(source, document.source());
        assertTrue(document.crop().isIdentity());
        assertSame(FilterState.NONE, document.filter());
    }

    @Test
    public void cropQuarterTurnsAreCanonical() {
        CropState crop = new CropState(.5f, .5f, 1f, 1f, 0f, -1, false);
        assertEquals(3, crop.quarterTurns());
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidSourceDimensionsAreRejected() {
        new SourceImage("invalid", 0, 100);
    }
}

