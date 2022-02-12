import processing.core.*;

import g4p_controls.*;

public class AutoLayout extends PApplet {
	private int lastWidth = 0;
	private int lastHeight = 0;

	private LayoutManager layoutManager = new LayoutManager(this);

	public void setup() {
		layoutManager.init("data/headphones.png");
		//layoutManager.init("data/test2.png");

		layoutManager.resetBubbles();

		G4P.setGlobalColorScheme(GConstants.CYAN_SCHEME);

		surface.setTitle("AutoLayout");
		surface.setResizable(true);
		surface.setLocation(100, 100);

		smooth();

		frameRate(60);

		/*

		int processStart = millis();

		while(!layoutManager.calculationDone)
			layoutManager.process();

		PApplet.println("Calc time = " + Integer.toString((millis() - processStart)));
		*/
	}

	@Override
	public void settings() {
		super.settings();

		size(700, 700);
	}

	void checkResizing() {
		if (this.lastWidth == width && this.lastHeight == height)
			return;

		this.lastWidth = width;
		this.lastHeight = height;

		this.layoutManager.posX = 20;
		this.layoutManager.posY = 20;
		this.layoutManager.areaWidth = height - 40;
		this.layoutManager.areaHeight = height - 40;
	}

	int lastDrawTime = 0;

	public void draw() {
		// отладка, помедленнее показывать
		// if(millis() - lastDrawTime < 100)
		//	return;

		layoutManager.process();

		if(layoutManager.calculationDone)
		{
			delay(3000);
			layoutManager.resetBubbles();
		}

		if(millis() - lastDrawTime < 50)
			return;

		lastDrawTime = millis();

		checkResizing();

		background(0x4B4B4B);

		layoutManager.draw();
	}

	public static void main(String[] args) {
		String[] processingArgs = { "AutoLayout" };
		AutoLayout mySketch = new AutoLayout();
		PApplet.runSketch(processingArgs, mySketch);
	}
}
