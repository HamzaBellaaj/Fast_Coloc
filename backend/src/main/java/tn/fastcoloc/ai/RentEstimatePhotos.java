package tn.fastcoloc.ai;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.List;
import javax.imageio.ImageIO;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import tn.fastcoloc.api.ApiException;

/** The existing workflow reads file0: a numbered overview lets it see every photo. */
final class RentEstimatePhotos {
    record Photo(byte[] bytes, String contentType, String extension) {}

    static Photo prepare(List<MultipartFile> photos) throws IOException {
        if (photos == null || photos.isEmpty() || photos.size() > 10)
            throw invalid("Ajoute entre 1 et 10 photos du logement.");
        for (var photo : photos) {
            if (photo.isEmpty() || photo.getSize() > 5242880)
                throw invalid("Chaque photo doit peser entre 1 octet et 5 MiB.");
            if (photo.getContentType() == null || !List.of("image/jpeg", "image/png", "image/webp").contains(photo.getContentType()))
                throw invalid("Utilise des photos JPEG, PNG ou WebP.");
        }
        var first = photos.getFirst();
        if (photos.size() == 1)
            return new Photo(first.getBytes(), first.getContentType(), first.getContentType().substring(6).replace("jpeg", "jpg"));

        int columns = Math.min(3, photos.size());
        int rows = (photos.size() + columns - 1) / columns;
        var overview = new BufferedImage(columns * 640, rows * 480, BufferedImage.TYPE_INT_RGB);
        var graphics = overview.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, overview.getWidth(), overview.getHeight());
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
            for (int i = 0; i < photos.size(); i++) {
                // Subsample while decoding, rather than allocating ten full-resolution photos.
                try (var source = photos.get(i).getInputStream(); var input = ImageIO.createImageInputStream(source)) {
                    var readers = ImageIO.getImageReaders(input);
                    if (!readers.hasNext()) throw invalid("La photo " + (i + 1) + " est illisible.");
                    var reader = readers.next();
                    try {
                        reader.setInput(input);
                        int width = reader.getWidth(0), height = reader.getHeight(0);
                        if (width < 1 || height < 1 || width > 20000 || height > 20000)
                            throw invalid("Dimensions invalides pour la photo " + (i + 1) + ".");
                        var params = reader.getDefaultReadParam();
                        int sample = Math.max(1, Math.min(width / 620, height / 440));
                        params.setSourceSubsampling(sample, sample, 0, 0);
                        var image = reader.read(0, params);
                        double scale = Math.min(620.0 / image.getWidth(), 440.0 / image.getHeight());
                        int w = Math.max(1, (int) (image.getWidth() * scale));
                        int h = Math.max(1, (int) (image.getHeight() * scale));
                        int x = (i % columns) * 640, y = (i / columns) * 480;
                        graphics.setColor(Color.BLACK);
                        graphics.drawString("Photo " + (i + 1) + " / " + photos.size(), x + 12, y + 25);
                        graphics.drawImage(image, x + (640 - w) / 2, y + 34 + (440 - h) / 2, w, h, null);
                        image.flush();
                    } finally { reader.dispose(); }
                }
            }
        } finally { graphics.dispose(); }
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(overview, "jpg", bytes);
        overview.flush();
        return new Photo(bytes.toByteArray(), "image/jpeg", "jpg");
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "ESTIMATE_PHOTOS_INVALID", message);
    }
}
