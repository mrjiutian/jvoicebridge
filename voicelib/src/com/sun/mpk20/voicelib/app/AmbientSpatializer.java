/*
 * Copyright 2007 Sun Microsystems, Inc.
 *
 * This file is part of jVoiceBridge.
 *
 * jVoiceBridge is free software: you can redistribute it and/or modify 
 * it under the terms of the GNU General Public License version 2 as 
 * published by the Free Software Foundation and distributed hereunder 
 * to you.
 *
 * jVoiceBridge is distributed in the hope that it will be useful, 
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Sun designates this particular file as subject to the "Classpath"
 * exception as provided by Sun in the License file that accompanied this 
 * code. 
 */
package com.sun.mpk20.voicelib.app;

import com.sun.sgs.app.AppContext;

import java.io.Serializable;

public class AmbientSpatializer implements Spatializer, Serializable {
    double minX;
    double maxX;
    double minY;
    double maxY;
    double minZ;
    double maxZ;

    double attenuator = Spatializer.DEFAULT_MAXIMUM_VOLUME;

    private double scale = 1.0;

    public AmbientSpatializer() {
    }
	
    public AmbientSpatializer(double lowerLeftX, double lowerLeftY,
 	double lowerLeftZ, double upperRightX,
	double upperRightY, double upperRightZ) {

	setBounds(lowerLeftX, lowerLeftY, lowerLeftZ,
	    upperRightX, upperRightY, upperRightZ);

        scale =  AppContext.getManager(VoiceManager.class).getVoiceManagerParameters().scale;
    }
        
    public void setBounds(double lowerLeftX, double lowerLeftY,
	double lowerLeftZ, double upperRightX,
	double upperRightY, double upperRightZ) {

	//logger.finest("lX " + lowerLeftX + " lY " + lowerLeftY
	//	+ " lZ " + lowerLeftZ + " uX " + upperRightX
	//	+ " uY " + upperRightY + " uZ " + upperRightZ);

        minX = Util.round100(Math.min(lowerLeftX / scale, upperRightX / scale));
        maxX = Util.round100(Math.max(lowerLeftX / scale, upperRightX / scale));
        minY = Util.round100(Math.min(lowerLeftY / scale, upperRightY / scale));
        maxY = Util.round100(Math.max(lowerLeftY / scale, upperRightY / scale));
        minZ = Util.round100(Math.min(lowerLeftZ / scale, upperRightZ / scale));
        maxZ = Util.round100(Math.max(lowerLeftZ / scale, upperRightZ / scale));
    }

    public double[] spatialize(double sourceX, double sourceY, 
                               double sourceZ, double sourceOrientation, 
                               double destX, double destY, 
                               double destZ, double destOrientation)
    {
	// see if the destination is inside the ambient audio range
        if (isInside(destX, destY, destZ)) {
	    //logger.finest("inside min (" + round(minX) + ", " + round(minY) 
	    //  + ", " + round(minZ) + ") "
	    //  + " max (" + round(maxX) + ", " + round(maxY) + ", " 
	    //  + round(maxZ) + ") " + " dest (" + round(destX) + ", " 
	    //  + round(destY) + ", " + round(destZ) + ")");
	    return new double[] { 0, 0, 0, attenuator };
        } else {
	    //logger.info("outside min (" + round(minX) + ", " + round(minY) 
	    //  + ", " + round(minZ) + ") "
	    //  + " max (" + round(maxX) + ", " + round(maxY) + ", " 
	    //  + round(maxZ) + ") " + " dest (" + round(destX) + ", " 
	    //  + round(destY) + ", " + round(destZ) + ")");
            return new double[] { 0, 0, 0, 0 };
        }
    }
        
    private boolean isInside(double x, double y, double z) {
	//System.out.println("isInside: x " + x + " y " + y + " z " + z
	//	+ " minX " + minX + " maxX " + maxX
	//	+ " minY " + minY + " maxY " + maxY
	//	+ " minZ " + minZ + " maxZ " + maxZ); 

	/*
	 * Don't check z because we always expect 0, but it's always
	 * non-zero.
	 */
        return x >= minX && x <= maxX && y >= minY && y <= maxY &&
	    z >= minZ && z <= maxZ;
    }

    public void setAttenuator(double attenuator) {
	this.attenuator = attenuator;
    }

    public double getAttenuator() {
	return attenuator;
    }

    public Object clone() {
        AmbientSpatializer a = new AmbientSpatializer();

	a.minX = minX;
        a.maxX = maxX;
        a.minY = minY;
        a.maxY = maxY;
        a.minZ = minZ;
        a.maxZ = maxZ;
	a.attenuator = attenuator;

	return a;
    }

    public String toString() {
	return "AmbientSpatializer(minX=" + minX + " maxX=" + maxX
	    + " minY=" + minY + " maxY=" + maxY + " minZ=" + minZ 
	    + " maxZ=" + maxZ + " attenuator=" + attenuator + ")";
    }

}
