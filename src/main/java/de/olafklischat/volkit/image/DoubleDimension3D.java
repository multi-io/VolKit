package de.olafklischat.volkit.image;

public class DoubleDimension3D
{
	protected double width;
	protected double height;
	protected double depth;
	
	public DoubleDimension3D( double width, double height, double depth )
	{
		this.width = width;
		this.height = height;
		this.depth = depth;
	}
	
	public double getDepth()
	{
		return depth;
	}
	
	public double getHeight()
	{
		return height;
	}
	
	public double getMax()
	{
		return Math.max( Math.max( width, height ), depth );
	}
	
	public double getMin()
	{
		return Math.min( Math.min( width, height ), depth );
	}
	
	public double getWidth()
	{
		return width;
	}
	
	public void setDepth( double depth )
	{
		this.depth = depth;
	}
	
	public void setHeight( double height )
	{
		this.height = height;
	}
	
	public void setWidth( double width )
	{
		this.width = width;
	}
	
	@Override
	public String toString()
	{
		return "[" + width + ", " + height + ", " + depth + "]";
	}

	public double[] toDoubleArray() {
		double[] d = {width, height, depth};
		
		return d;
	}
	
	
	
}