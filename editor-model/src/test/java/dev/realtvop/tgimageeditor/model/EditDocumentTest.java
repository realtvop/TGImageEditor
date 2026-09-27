package dev.realtvop.tgimageeditor.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;

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

    @Test
    public void filterUpdatesPreserveOtherParameters() {
        FilterState state = FilterState.NONE.withExposure(.25f).withSaturation(-.5f);
        assertEquals(.25f, state.exposure(), .0001f);
        assertEquals(-.5f, state.saturation(), .0001f);
        assertEquals(0f, state.contrast(), .0001f);
        assertTrue(FilterState.NONE.isIdentity());
    }

    @Test
    public void documentCopiesPaintInput() {
        PaintStroke stroke = new PaintStroke(Arrays.asList(new PaintPoint(.1f, .2f)), 0xffffffff, .02f);
        java.util.ArrayList<PaintStroke> strokes = new java.util.ArrayList<>();
        strokes.add(stroke);
        EditDocument document = EditDocument.create(new SourceImage("test", 100, 100)).withPaintStrokes(strokes);
        strokes.clear();
        assertEquals(1, document.paintStrokes().size());
    }

    @Test
    public void textTransformClampsPositionAndScale() {
        TextEntity text = new TextEntity("Hello", .5f, .5f, .08f, 1f, 0f,
                0xffffffff, TextEntity.Style.OUTLINE);
        TextEntity moved = text.withTransform(2f, -1f, 10f, 30f);
        assertEquals(1f, moved.x(), .0001f);
        assertEquals(0f, moved.y(), .0001f);
        assertEquals(4f, moved.scale(), .0001f);
        assertEquals(.5f, text.x(), .0001f);
    }

    @Test
    public void toneCurveMapsControlPoints() {
        ToneCurve curve = ToneCurve.LINEAR.withMidtones(.75f);
        assertEquals(191, curve.map(128), 2);
        assertEquals(0, curve.map(0));
        assertEquals(255, curve.map(255));
    }

    @Test
    public void advancedFilterStateRemainsImmutable() {
        FilterState changed = FilterState.NONE.withSharpen(.4f)
                .withBlur(BlurState.NONE.withType(BlurState.Type.RADIAL));
        assertTrue(FilterState.NONE.isIdentity());
        assertEquals(.4f, changed.sharpen(), .0001f);
        assertEquals(BlurState.Type.RADIAL, changed.blur().type());
    }

    @Test
    public void documentCanRoundTripThroughSavedInstanceStateSerialization() throws Exception {
        EditDocument original = EditDocument.create(new SourceImage("content://image", 800, 600))
                .withCrop(CropState.FULL_IMAGE.withBounds(.1f, .2f, .9f, .8f))
                .withFilter(FilterState.NONE.withExposure(.25f))
                .withDrawing(
                        Arrays.asList(new PaintStroke(
                                Arrays.asList(new PaintPoint(.2f, .3f), new PaintPoint(.4f, .5f)),
                                0xff336699, .03f, PaintStroke.Kind.NEON)),
                        Arrays.asList(new TextEntity("Saved", .5f, .4f, .08f, 1.2f, 15f,
                                0xffffffff, TextEntity.Style.FRAME)));

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(original);
        }
        EditDocument restored;
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            restored = (EditDocument) input.readObject();
        }

        assertEquals(original.source().id(), restored.source().id());
        assertEquals(.1f, restored.crop().left(), .0001f);
        assertEquals(.25f, restored.filter().exposure(), .0001f);
        assertEquals(PaintStroke.Kind.NEON, restored.paintStrokes().get(0).kind());
        assertEquals("Saved", restored.textEntities().get(0).text());
    }
}
