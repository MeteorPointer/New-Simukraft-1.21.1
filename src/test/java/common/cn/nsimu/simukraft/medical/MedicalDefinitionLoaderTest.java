package common.cn.nsimu.simukraft.medical;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MedicalDefinitionLoaderTest {
    @Test
    void serviceRangeRingsAreClampedToOneThroughSix() {
        MedicalDefinitionLoader.LoadResult belowMinimum = MedicalDefinitionLoader.parse(
                "{\"id\":\"small\",\"serviceRangeRings\":0}", "fallback", null);
        MedicalDefinitionLoader.LoadResult aboveMaximum = MedicalDefinitionLoader.parse(
                "{\"id\":\"large\",\"serviceRangeRings\":9}", "fallback", null);

        assertTrue(belowMinimum.valid());
        assertTrue(aboveMaximum.valid());
        assertEquals(1, belowMinimum.definition().serviceRangeRings());
        assertEquals(6, aboveMaximum.definition().serviceRangeRings());
    }

    @Test
    void missingRangeUsesThreeRingDefaultAndMalformedJsonIsRejected() {
        MedicalDefinitionLoader.LoadResult defaults = MedicalDefinitionLoader.parse(
                "{\"id\":\"hospital\"}", "fallback", null);
        MedicalDefinitionLoader.LoadResult malformed = MedicalDefinitionLoader.parse("{", "fallback", null);

        assertEquals(3, defaults.definition().serviceRangeRings());
        assertFalse(malformed.valid());
    }

    @Test
    void ringCountMatchesChebyshevChunkSquares() {
        assertEquals(1, MedicalService.coveredChunkCount(1));
        assertEquals(9, MedicalService.coveredChunkCount(2));
        assertEquals(25, MedicalService.coveredChunkCount(3));
        assertEquals(121, MedicalService.coveredChunkCount(6));
    }
}
