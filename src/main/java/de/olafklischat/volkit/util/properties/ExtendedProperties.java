package de.olafklischat.volkit.util.properties;

import java.io.*;
import java.net.*;
import java.util.*;

//erleichterter Zugriff auf Properties
@SuppressWarnings("serial")
public class ExtendedProperties extends Properties
{
	public ExtendedProperties(String fileName) throws IOException
	{
		super();
		
		URL url = this.getClass().getClassLoader().getResource(fileName);
                
                if (url == null)
                    throw new IOException("not found: " + fileName);
                
                this.load(url.openStream());
		
	}
	
	public boolean getB(String key)
	{
		return ("true".equals(getProperty(key)) || "yes".equals(getProperty(key)));
	}
	
	public double getD(String key) throws NumberFormatException
	{
		return Double.parseDouble(getProperty(key));
	}
	
	public float getF(String key) throws NumberFormatException
	{
		return Float.parseFloat(getProperty(key));
	}
	
	public int getI(String key) throws NumberFormatException
	{
		return Integer.parseInt(getProperty(key));
	}
	
	public long getL(String key) throws NumberFormatException
	{
		return Long.parseLong(getProperty(key));
	}
	
}