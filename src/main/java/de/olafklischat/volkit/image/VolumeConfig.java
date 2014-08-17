package de.olafklischat.volkit.image;

import de.olafklischat.volkit.util.properties.ExtendedProperties;

public class VolumeConfig {

	protected int availableGraphicsMemory = 768;
	
	protected ExtendedProperties properties;
	
	protected VolumeBasicConfig basicConfig;

	protected VolumeWindowingConfig windowingConfig;

	public VolumeConfig(ExtendedProperties properties) {
		this.properties = properties;
		
		basicConfig = new VolumeBasicConfig(properties);
		this.availableGraphicsMemory = properties.getI("volumeConfig.availableGraphicsMemory");
	}

	public int getAvailableGraphicsMemory() {
		return availableGraphicsMemory;
	}

	public VolumeBasicConfig getBasicConfig() {
		return basicConfig;
	}

	public ExtendedProperties getProperties() {
		return properties;
	}

	public long getVolumeMemory() {
		int nrOfLoadedImages = basicConfig.getNrOfLoadedImages();

		return (((long)basicConfig.getPixelWidth() * basicConfig.getPixelHeight() * nrOfLoadedImages * basicConfig.getInternalPixelFormatBits() )/ 8);
	}

	public VolumeWindowingConfig getWindowingConfig() {
		return windowingConfig;
	}

	public long getWindowingMemory() {
		if (windowingConfig.getUsage() == VolumeWindowingConfig.WindowingUsage.WINDOWING_USAGE_NO || !windowingConfig.isUsePreCalculated())
			return 0;

		int nrOfLoadedImages = basicConfig.getNrOfLoadedImages();

		return (((long)basicConfig.getPixelWidth() * basicConfig.getPixelHeight() * nrOfLoadedImages * windowingConfig.getTargetPixelFormat()) / 8);
	}

	public void setAvailableGraphicsMemory(int availableGraphicsMemory) {
		this.availableGraphicsMemory = availableGraphicsMemory;
	}

	public void setBasicConfig(VolumeBasicConfig basicConfig) {
		this.basicConfig = basicConfig;
	}

	public void setWindowingConfig(VolumeWindowingConfig windowingConfig) {
		this.windowingConfig = windowingConfig;
	}

}