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

    @Test
    public void cropBoundsRoundTrip() {
        CropState crop = CropState.FULL_IMAGE.withBounds(.1f, .2f, .8f, .9f);
        assertEquals(.1f, crop.left(), .0001f);
        assertEquals(.2f, crop.top(), .0001f);
        assertEquals(.8f, crop.right(), .0001f);
        assertEquals(.9f, crop.bottom(), .0001f);
    }

    @Test
    public void cropTransformUpdatesAreImmutable() {
        CropState original = CropState.FULL_IMAGE;
        CropState changed = original.rotateClockwise().toggleMirror();
        assertTrue(original.isIdentity());
        assertEquals(1, changed.quarterTurns());
        assertTrue(changed.mirrored());
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidSourceDimensionsAreRejected() {
        new SourceImage("invalid", 0, 100);
    }
}
