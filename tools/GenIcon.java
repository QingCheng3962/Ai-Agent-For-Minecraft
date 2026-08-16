import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;

public final class GenIcon {
	public static void main(String[] args) throws Exception {
		int size = 128;
		BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		g.setColor(new Color(0x1B, 0x1B, 0x2A));
		g.fillRoundRect(0, 0, size, size, 24, 24);

		g.setColor(new Color(0x35, 0x3D, 0x56));
		g.fillRoundRect(18, 18, size - 36, size - 36, 18, 18);

		g.setColor(new Color(0x7C, 0xD6, 0xFF));
		g.setStroke(new BasicStroke(6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.drawLine(28, 74, 70, 74);
		g.drawLine(70, 74, 58, 58);
		g.drawLine(70, 74, 58, 90);

		g.setColor(new Color(0x8B, 0xFF, 0xA0));
		g.fillOval(30, 42, 10, 10);
		g.fillOval(88, 42, 10, 10);
		g.fillOval(30, 78, 10, 10);
		g.fillOval(88, 78, 10, 10);

		g.setColor(new Color(0xF5, 0xF5, 0xF5));
		g.setStroke(new BasicStroke(4f));
		g.drawOval(44, 56, 40, 24);

		g.dispose();

		File out = new File(args[0]);
		out.getParentFile().mkdirs();
		ImageIO.write(img, "png", out);
		System.out.println("wrote " + out.getAbsolutePath());
	}
}
