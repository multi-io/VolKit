package de.olafklischat.volkit.image;

import de.olafklischat.volkit.util.properties.ExtendedProperties;

public class VolumeWindowingConfig {
	public enum WindowingModification {
		WINDOWING_MODIFICATION_SLICE_AND_VOLUME_VIEW(0), WINDOWING_MODIFICATION_SLICE_VIEW_ONLY(1), WINDOWING_MODIFICATION_NO(2);

		private final int value;

		WindowingModification(int val) {
			this.value = val;
		}

		public int value() {
			return value;
		}
	}

	public enum WindowingUsage {
		WINDOWING_USAGE_ORIGINAL(0), WINDOWING_USAGE_RANGE_GLOBAL(1), WINDOWING_USAGE_NO(2);

		private final int value;

		WindowingUsage(int val) {
			this.value = val;
		}

		public int value() {
			return value;
		}
	}

	protected WindowingModification modification = WindowingModification.WINDOWING_MODIFICATION_SLICE_AND_VOLUME_VIEW;
	protected int targetPixelFormat;
	protected WindowingUsage usage = WindowingUsage.WINDOWING_USAGE_ORIGINAL;
	protected boolean usePreCalculated = false;

	public VolumeWindowingConfig(ExtendedProperties properties) {
		usePreCalculated = properties.getB("volumeConfig.windowing.usePreCalculated");

		String mod = properties.getProperty("volumeConfig.windowing.modification");

		if ("NO".equals(mod))
			modification = WindowingModification.WINDOWING_MODIFICATION_NO;
		else if ("SLICE_AND_VOLUME_VIEW".equals(mod))
			modification = WindowingModification.WINDOWING_MODIFICATION_SLICE_AND_VOLUME_VIEW;
		else if ("SLICE_VIEW_ONLY".equals(mod))
			modification = WindowingModification.WINDOWING_MODIFICATION_SLICE_VIEW_ONLY;

		String us = properties.getProperty("volumeConfig.windowing.usage");

		if ("NO".equals(us))
			usage = WindowingUsage.WINDOWING_USAGE_NO;
		else if ("ORIGINAL".equals(us))
			usage = WindowingUsage.WINDOWING_USAGE_ORIGINAL;
		else if ("RANGE_GLOBAL".equals(us))
			usage = WindowingUsage.WINDOWING_USAGE_RANGE_GLOBAL;

	}

	public WindowingModification getModification() {
		return modification;
	}

	public int getTargetPixelFormat() {
		return targetPixelFormat;
	}

	public WindowingUsage getUsage() {
		return usage;
	}

	public boolean isUsePreCalculated() {
		return usePreCalculated;
	}

	public void setModification(WindowingModification modification) {
		this.modification = modification;
	}

	public void setTargetPixelFormat(int targetPixelFormat) {
		this.targetPixelFormat = targetPixelFormat;
	}

	public void setUsage(WindowingUsage usage) {
		this.usage = usage;
	}

	public void setUsePreCalculated(boolean usePreCalculated) {
		this.usePreCalculated = usePreCalculated;
	}

}