package com.mylife.pacs.infrastructure.dimse;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class Dcm4cheAttributesBridgeTest {

    @Test
    void shouldPreservePixelDataPresenceMarkersWithoutRehydratingThemIntoDatasets() {
        Attributes dataset = new Attributes();
        dataset.setString(Tag.PatientID, VR.LO, "patient-1");
        dataset.setBytes(Tag.PixelData, VR.OB, new byte[]{1, 2, 3});
        dataset.setBytes(Tag.FloatPixelData, VR.OF, new byte[]{4, 5, 6, 7});
        dataset.setBytes(Tag.DoubleFloatPixelData, VR.OD, new byte[]{8, 9, 10, 11, 12, 13, 14, 15});

        Map<String, String> mapped = Dcm4cheAttributesBridge.toAttributeMap(dataset);

        assertEquals("patient-1", mapped.get("00100020"));
        assertEquals("__present__", mapped.get("7FE00010"));
        assertEquals("__present__", mapped.get("7FE00008"));
        assertEquals("__present__", mapped.get("7FE00009"));

        Attributes rebuilt = Dcm4cheAttributesBridge.toDataset(mapped);

        assertEquals("patient-1", rebuilt.getString(Tag.PatientID));
        assertFalse(rebuilt.contains(Tag.PixelData));
        assertFalse(rebuilt.contains(Tag.FloatPixelData));
        assertFalse(rebuilt.contains(Tag.DoubleFloatPixelData));
    }
}
