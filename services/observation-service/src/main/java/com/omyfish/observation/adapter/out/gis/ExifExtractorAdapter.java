package com.omyfish.observation.adapter.out.gis;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.omyfish.observation.domain.model.valueobject.ExifMetadata;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.time.Instant;

@Component
public class ExifExtractorAdapter {

    public ExifMetadata extract(InputStream imageStream) {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(imageStream);

            ExifSubIFDDirectory subIfd = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
            Instant capturedAt = null;
            Integer width = null, height = null;
            Double focalLength = null, aperture = null;
            Integer iso = null;
            if (subIfd != null) {
                var date = subIfd.getDateOriginal();
                if (date != null) capturedAt = date.toInstant();
                width = subIfd.getInteger(ExifSubIFDDirectory.TAG_EXIF_IMAGE_WIDTH);
                height = subIfd.getInteger(ExifSubIFDDirectory.TAG_EXIF_IMAGE_HEIGHT);
                var fl = subIfd.getRational(ExifSubIFDDirectory.TAG_FOCAL_LENGTH);
                if (fl != null) focalLength = fl.doubleValue();
                var ap = subIfd.getRational(ExifSubIFDDirectory.TAG_FNUMBER);
                if (ap != null) aperture = ap.doubleValue();
                iso = subIfd.getInteger(ExifSubIFDDirectory.TAG_ISO_EQUIVALENT);
            }

            ExifIFD0Directory ifd0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            String cameraModel = null;
            if (ifd0 != null) {
                String make = ifd0.getString(ExifIFD0Directory.TAG_MAKE);
                String model = ifd0.getString(ExifIFD0Directory.TAG_MODEL);
                cameraModel = (make != null && model != null) ? make + " " + model
                    : (model != null ? model : make);
            }

            return new ExifMetadata(capturedAt, cameraModel, width, height, focalLength, aperture, iso);
        } catch (Exception e) {
            return ExifMetadata.empty();
        }
    }
}
