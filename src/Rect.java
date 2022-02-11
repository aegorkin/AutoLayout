import processing.core.*;

public class Rect {
	public PVector min, max;

	public Rect() {
		this.min = new PVector(Float.MAX_VALUE, Float.MAX_VALUE);
		this.max = new PVector(Float.MIN_VALUE, Float.MIN_VALUE);
	}

	public Rect(PVector aMin, PVector aMax) {
		this.min = aMin;
		this.max = aMax;
	}

	public Rect(float xMin, float yMin, float xMax, float yMax) {
		this.min = new PVector(xMin, yMin);
		this.max = new PVector(xMax, yMax);
	}

	public Boolean isEmpty() {
		return this.min.x > this.max.x || this.min.y > this.max.y;
	}

	public Boolean isPointInside(PVector p) {
		if(p.x < this.min.x || p.x > this.max.x || p.y < this.min.y || p.y > this.max.y)
			return false;

		return true;
	}

	public Rect getIntersection(Rect otherRect) {
		if(this.min.x > otherRect.max.x || this.min.y > otherRect.max.y || this.max.x < otherRect.min.x || this.max.y < otherRect.min.y)
			return new Rect();

		PVector newMin = new PVector(Math.max(this.min.x, otherRect.min.x), Math.max(this.min.y, otherRect.min.y));
		PVector newMax = new PVector(Math.min(this.max.x, otherRect.max.x), Math.min(this.max.y, otherRect.max.y));

		return new Rect(newMin, newMax);
	}

	public float getSquare() {
		if(this.isEmpty())
			return 0.0f;

		return (max.x - min.x)*(max.y-min.y);
	}

	public PVector getCenter() {
		return new PVector((this.min.x + this.max.x)*0.5f, (this.min.y + this.max.y)*0.5f);
	}

	public float getWidth() {
		return this.max.x - this.min.x;
	}

	public float getHeight() {
		return this.max.y - this.min.y;
	}

	// возвращает вектор, на который надо сместить прямоугольник, чтобы вписать в прямоугольник из аргумента
	public PVector getShiftToFit(Rect borderRect) {
		PVector shift = new PVector(0.0f, 0.0f);

		if(this.min.x < borderRect.min.x)
			shift.x = borderRect.min.x - this.min.x;

		if(this.min.y < borderRect.min.y)
			shift.y = borderRect.min.y - this.min.y;

		if(this.max.x > borderRect.max.x)
			shift.x = (borderRect.max.x - this.max.x);

		if(this.max.y > borderRect.max.y)
			shift.y = (borderRect.max.y - this.max.y);

		return shift;
	}
}
