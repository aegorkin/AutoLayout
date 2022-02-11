import java.nio.*;
import java.text.DecimalFormat;

import processing.core.*;
import processing.serial.*;

public class Routines {

    public static int Serial_readInt32LE(Serial serial) {
        byte[] inBuffer;

        while ((inBuffer = serial.readBytes(4)) == null)
            ;

        assert inBuffer != null;
        assert inBuffer.length == 4;

        ByteBuffer bb = ByteBuffer.wrap(inBuffer);
        bb.order(ByteOrder.LITTLE_ENDIAN);

        return bb.getInt();
    }

    public static short Serial_readInt16LE(Serial serial) {
        byte[] inBuffer;

        while ((inBuffer = serial.readBytes(2)) == null)
            ;

        assert inBuffer != null;
        assert inBuffer.length == 2;

        ByteBuffer bb = ByteBuffer.wrap(inBuffer);
        bb.order(ByteOrder.LITTLE_ENDIAN);

        return bb.getShort();
    }

	public static float Serial_readFloatLE(Serial serial) {
		byte[] inBuffer;

		while(true)
		{
			inBuffer = serial.readBytes(Float.BYTES);

			if(inBuffer != null)
			{
				if(inBuffer.length == Float.BYTES)
					break;
			}
		}

		ByteBuffer bb = ByteBuffer.wrap(inBuffer);
		bb.order(ByteOrder.LITTLE_ENDIAN);

		return bb.getFloat();
	}

    public static byte Serial_readByte(Serial serial) {
        int res;

        while ((res = serial.read()) == -1)
            ;

        assert res != -1;

        return (byte)res;
    }

    public static void Serial_writeInt32LE(Serial serial, int value) {
        ByteBuffer bb = ByteBuffer.allocate(4);
        bb.order(ByteOrder.LITTLE_ENDIAN);
        bb.putInt(value);

        serial.write(bb.array());
    }

    public static void Serial_writeInt16LE(Serial serial, short value) {
        ByteBuffer bb = ByteBuffer.allocate(2);
        bb.order(ByteOrder.LITTLE_ENDIAN);
        bb.putShort(value);

        serial.write(bb.array());
    }

	public static void Serial_writeFloatLE(Serial serial, float value) {
		ByteBuffer bb = ByteBuffer.allocate(Float.BYTES);

		bb.order(ByteOrder.LITTLE_ENDIAN);

		bb.putFloat(value);

		serial.write(bb.array());
	}

    public static String Serial_readStringBinary(Serial serial, int size) {
		byte[] stringBytes;

		while (true)
		{
			stringBytes = serial.readBytes(size);

			if(stringBytes == null) continue;

			if(stringBytes.length == size) break;
		}

        assert stringBytes != null;
        assert stringBytes.length == size;

        String str = new String();

        for (int i = 0; i < size; i++) {
            if (stringBytes[i] == 0)
                break;

            str += (char) stringBytes[i];
        }

        return str;
    }

    public static String getDateTimeString() {
        return PApplet.year() + "-" + PApplet.month() + "-" + PApplet.day() + " " + PApplet.hour() + "-" + PApplet.minute() + "-" + PApplet.second();
    }

	public static String floatToStringComfortReading(float value)
	{
		String decFmt = "#.";

		int decimals = 3;

		if(Math.abs(value)  > 1.0f) decimals --;
		if(Math.abs(value)  > 10.0f) decimals --;

		for(int i = 0; i < decimals; i++)
			decFmt += '#';

		DecimalFormat df = new DecimalFormat(decFmt);

		return df.format(value);
	}

    public static float sqr(float x)
    {
        return x*x;
    }
}