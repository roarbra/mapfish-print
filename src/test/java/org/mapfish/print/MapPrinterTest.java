package org.mapfish.print;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Optional;

import javax.imageio.ImageIO;

import org.junit.Test;
import org.mapfish.print.utils.PJsonObject;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * Testing MapPrinter with full support from a Geoserver perspective.
 * Using the same applicationContext as in Geoserver.
 * 
 * @author Roar Brænden, NIVA
 *
 */
public class MapPrinterTest {
	
	static {
		System.setProperty("com.sun.media.jai.disableMediaLib", "true");
	}
	
	@Test
	public void testOpenStreetMapTileColorAnomalies() throws Exception {
		String spec = "{" + 
				"  \"layout\":\"Image\"," + 
				"  \"srs\":\"EPSG:900913\"," + 
				"  \"units\":\"meters\"," + 
				"  \"geodetic\":false," + 
				"  \"outputFormat\":\"png\"," + 
				"  \"dpi\":300," + 
				"  \"layers\":[{" + 
				"    \"type\":\"OSM\"," + 
				"    \"baseURL\":\"http://tile.openstreetmap.org/\"," + 
				"    \"maxExtent\":[-20037508.34,-20037508.34,20037508.34,20037508.34]," + 
				"    \"tileSize\":[256,256]," + 
				"    \"resolutions\":[156543.03390625,78271.516953125,39135.7584765625,19567.87923828125,9783.939619140625," + 
				"    4891.9698095703125,2445.9849047851562,1222.9924523925781,611.4962261962891,305.74811309814453," + 
				"    152.87405654907226,76.43702827453613,38.218514137268066,19.109257068634033,9.554628534317017," + 
				"    4.777314267158508,2.388657133579254,1.194328566789627,0.5971642833948135]," + 
				"    \"extension\":\"png\"" + 
				"  }]" + 
				"  ,\"pages\":[{\"bbox\":[9854210.4540103,1681670.9768253,11615319.585456,3124802.0706485]}]}";
		
		PJsonObject specJson = MapPrinter.parseSpec(spec);
		MapPrinter printer = startMapPrinter();
		HashMap<String, String> headers = new HashMap<String, String>();

		File tempFile = Files.createTempFile("printing",
				"." + printer.getOutputFormat(specJson).getFileSuffix())
				.toFile();
		
		try {
	        try (FileOutputStream out  = new FileOutputStream(tempFile)) {
	        	printer.print(specJson, out, headers);
	        }
	        Files.copy(tempFile.toPath(), Paths.get("C:/temp/printing-result.png"), StandardCopyOption.REPLACE_EXISTING);
	        
	        BufferedImage image = ImageIO.read(tempFile);
			assertNotNull("Didn't get an image.", image);

			CountColor typeColorOcean = computeTypeColor(image.getData(new Rectangle(375, 2000, 10, 10)));
			CountColor typeColorLowerLeft = computeTypeColor(image.getData(new Rectangle(125, 2000, 10, 10)));
			CountColor typeColorLowerRight = computeTypeColor(image.getData(new Rectangle(500, 2350, 10, 10)));
			
			assertEquals("Color in lower left corner", typeColorOcean, typeColorLowerLeft);
			assertEquals("Color a little bit longer to right", typeColorOcean, typeColorLowerRight);
		}
        finally  {
	        if (printer != null) {
	        	printer.stop();
	        }
        	tempFile.delete();
        }
	}
	
	
	private MapPrinter startMapPrinter() throws IOException, URISyntaxException {
		File yamlFile = Paths.get(getClass().getClassLoader().getResource("org/mapfish/print/gs-config.yaml").toURI()).toFile();
		MapPrinter printer = getApplicationContext().getBean(MapPrinter.class).setYamlConfigFile(yamlFile);
		printer.start();
		return printer;
	}
	
	private ApplicationContext getApplicationContext() {
		return new ClassPathXmlApplicationContext("org/mapfish/print/gs-applicationContext.xml");
	}
	

	
	/**
	 * We grab a little clip from the image, and finds the most frequent color
	 * @param clip
	 * @return
	 */
	private CountColor computeTypeColor(Raster clip) {
		int height = clip.getHeight();
		int width = clip.getWidth();
		HashSet<CountColor> colors = new HashSet<CountColor>();
		
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int[] color = new int[4];
				clip.getPixel(clip.getMinX() + x,  clip.getMinY() + y, color);
				Optional<CountColor> optional = colors.stream()
												.filter((element) -> element.sameColor(color))
												.findFirst();
				if (optional.isPresent()) {
					optional.get().incrementCount();
				}
				else {
					colors.add(new CountColor(color));
				}
			}
		}
		
		return colors.stream().max(new Comparator<CountColor>() {

			@Override
			public int compare(CountColor arg0, CountColor arg1) {
				return Integer.compare(arg0.count, arg1.count);
			}
		}).get();
	}
	


	/**
	 * Helper class to find the most frequent color.
	 * 
	 * @author Roar Brænden
	 *
	 */
	static class CountColor {
		int count;
		int[] color;
		
		CountColor(int[] color) {
			this.color = color;
			this.count = 1;
		}
		
		void incrementCount() {
			count += 1;
		}
	
		boolean sameColor(int[] other) {
	
			if (other.length == this.color.length) {
				for (int i = 0; i < this.color.length; i++) {
					if (other[i] != this.color[i]) {
						return false;
					}
				}
				return true;
			}
			return false;
		}
		
		@Override
		public boolean equals(Object other) {
			if (other instanceof CountColor) {
				return sameColor(((CountColor)other).color);
			}
			return false;
		}
		
		@Override
		public String toString() {
			return String.format("[%d %d %d]", color[0], color[1], color[2]);
		}
	}
}
