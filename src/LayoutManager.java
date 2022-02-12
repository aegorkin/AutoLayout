import java.util.*;

import processing.core.*;

public class LayoutManager {
	// для определения коллизий и сил используем больший прямоугольник, чем бабл, чтобы было пустое пространство между
	final float MARGIN_SILHOUETTE_COLLISION 		= 150.0f;
	final float MARGIN_BUBBLE_COLLISION				= 20.0f;
	final float SIL_POINT_SIZE 						= 60.0f;

	// коэффициенты сил
	final float FORCE_FACTOR_SILHOUTTE					= 0.75f;
	final float FORCE_FACTOR_ATTRACTION_POINT			= 3.0f;
	final float FORCE_FACTOR_BUBBLE_LONGRANGE			= 5.0f;
	final float FORCE_FACTOR_BUBBLE_INTERSECTION		= 1.0f;
	final float FORCE_FACTOR_UI_INTERSECTION			= 1.0f;
	final float FORCE_FACTOR_PINS						= 1.0f;

	final int 		NUM_ATTRACTIONS_ON_AXIS 		= 2;
	final int 		NUM_BUBBLES 					= 5;
	final int 		MAX_ITERATIONS 					= 50;

	// Главной задачей оптимизации является покидание бабблами силуета модели,
	// остальные условия вторичны. Поэтому дополнительные условия подключаются не сразу,
	// давая возможность особенно хорошо решить основную задачу.
	// Константы ниже определяют итерацию, с которой подключается расчёт дополнительных задач (сил)
	final int 		ITER_START_CALC_BUBBLES_LONGRANGE		= 15;
	final int 		ITER_START_CALC_BUBBLES_INTERSECTION 	= 20;
	final int 		ITER_START_CALC_PINS 					= 15;

	public class SilhouettePoint {
		public PVector 	pos;
		public float 	weight; // "вес" точки, процент заполненности пиксела силуэтом (0-1)
		public Rect 	rect; 	// для ускорения опредения пересечений

		SilhouettePoint(PVector p, float w) {
			this.pos = p;
			this.weight = w;

			this.rect = new Rect(p.x - SIL_POINT_SIZE/2, p.y - SIL_POINT_SIZE/2, p.x + SIL_POINT_SIZE/2, p.y + SIL_POINT_SIZE/2);
		}
	}

	public class Pin {
		public PVector pos;

		Pin(PVector p)
		{
			this.pos = p;
		}
	}

	public class Bubble {
		ArrayList<Pin> pins = new ArrayList<Pin>(); // здесь хранятся только ссылки на пины из массива LayoutManager.pins
		public PVector pos, dim; // позиция центра бабла и его размеры
		public PVector vel; // текущая скорость

		//визуальная отладка
		public PVector intersectionCenter;
		public boolean isGlobalresultForce = false; 

		Bubble(PVector d) {
			this.vel = new PVector(0.0f, 0.0f);

			this.dim = d;
		}

		Bubble(Pin pin, PVector d) {
			this.pins.add(pin);

			this.pos = new PVector(pin.pos.x, pin.pos.y);

			this.vel = new PVector(0.0f, 0.0f);

			this.dim = d;
		}

		public Rect getRect() { // возвращает прямоугольник баббла
			PVector halfDim = PVector.mult(this.dim, 0.5f);

			PVector min = PVector.sub(this.pos, halfDim);
			PVector max = PVector.add(this.pos, halfDim);

			return new Rect(min, max);
		}

		public Rect getMarginRect(float margin) { // возвращает прямоугольник с полями
			Rect rect = this.getRect();

			PVector marginVec = new PVector(margin, margin);

			rect.min.sub(marginVec);
			rect.max.add(marginVec);

			return rect;
		}
	}

	/*
	Абстракция AttractionArea нужна для "выталкивания" баббла со сплошных областей силуета.
	На сплошных областях нет градиента (перепада) силуета, а значит и нет выталкивающей силы.
	Чтобы столкнуть баббл туда, где скорее всего будет уже градиент (и он дальше поедет сам),
	мы используем разбиение вьюпорта на несколько одинаковых прямоугольных областей.

	Для каждой такой области вычисляется точка "центра лёгкости" (attractionPoint) - 
	точка где меньше всего силует в заданной области.

	Туда и будет стремиться баббл при отстутвии силы силуета.
	*/

	public class AttractionArea {
		public Rect 	rect;
		public PVector 	attractionPoint;
		public float	weight;

		AttractionArea(Rect r) {
			this.rect = r;
		}
	}

	private ArrayList<SilhouettePoint>  silPoints 		= new ArrayList<SilhouettePoint>();
	private ArrayList<Pin>  			pins 			= new ArrayList<Pin>();
	private ArrayList<Bubble> 			bubbles 		= new ArrayList<Bubble>();
	private ArrayList<AttractionArea> 	attractionAreas = new ArrayList<AttractionArea>();
	private ArrayList<Rect>				uiElements		= new ArrayList<Rect>();

	private 		PApplet pApplet;

	public int 		posX, posY, areaWidth, areaHeight;

	public int 		numIterations = 0;
	public boolean 	calculationDone = false;

	public Rect		viewport;

	public LayoutManager(PApplet p) {
		this.pApplet = p;
		viewport = new Rect(0, 0, 1000, 1000);
	}

	public void calculateAttractionAreas() { // вычисляется один раз перед началом итераций
		float attractionAreaWidth = viewport.getWidth()/NUM_ATTRACTIONS_ON_AXIS;
		float attractionAreaHeight = viewport.getHeight()/NUM_ATTRACTIONS_ON_AXIS;

		for(int i = 0; i < this.NUM_ATTRACTIONS_ON_AXIS; i++)
			for(int j = 0; j < this.NUM_ATTRACTIONS_ON_AXIS; j++) {
				AttractionArea area = new AttractionArea(
						new Rect(i*attractionAreaWidth, j*attractionAreaHeight,
						(i+1)*attractionAreaWidth, (j+1)*attractionAreaHeight));

				this.attractionAreas.add(area);
			}

		for(AttractionArea area: this.attractionAreas) {
			PVector silWeightCenter = new PVector(0.0f, 0.0f);

			PVector areaCenter = area.rect.getCenter();

			float totalSilWeight = 0.0f;

			for(SilhouettePoint point: this.silPoints) {
				Rect intersectionRect = area.rect.getIntersection(point.rect);

				if(intersectionRect.isEmpty())
					continue;

				float weightedSquare = point.weight*intersectionRect.getSquare();

				totalSilWeight += weightedSquare;

				PVector delta = PVector.sub(intersectionRect.getCenter(), areaCenter);

				silWeightCenter.add(PVector.mult(delta, weightedSquare));
			}

			float areaSquare = area.rect.getSquare();

			area.weight = (areaSquare - totalSilWeight)/areaSquare;

			if(totalSilWeight > 0.0f)
				silWeightCenter.mult(1.0f/totalSilWeight);

			float silWeightScale = areaSquare/(areaSquare - totalSilWeight);

			silWeightCenter.mult(silWeightScale);

			PVector attractionPoint = PVector.sub(area.rect.getCenter(), silWeightCenter);

			area.attractionPoint = attractionPoint;
		}	
	}

	public void addBubbleWithRandomDim(PVector pos) {
		Pin pin = new Pin(pos);

		pins.add(pin);

		PVector bubbleDim = new PVector((float)(150 + Math.random()*100), (float)(20 + Math.random()*100));

		bubbles.add(new Bubble(pin, bubbleDim));
	}

	public void addBubbleWithRandomDimAndTwoPins(PVector pos1, PVector pos2) {
		Pin pin1 = new Pin(pos1);
		Pin pin2 = new Pin(pos2);

		pins.add(pin1);
		pins.add(pin2);

		PVector bubbleDim = new PVector((float)(150 + Math.random()*100), (float)(20 + Math.random()*100));

		Bubble bubble = new Bubble(bubbleDim);

		bubble.pins.add(pin1);
		bubble.pins.add(pin2);

		bubble.pos = PVector.add(pin1.pos, pin2.pos);

		bubble.pos.mult(0.5f); // начальное положение баббла между своих пинов

		bubbles.add(bubble);
	}

	public void init(String silImageFilename) {
		PImage img = pApplet.loadImage(silImageFilename);

		img.loadPixels();

		int pixel = 0;

		float xShift = SIL_POINT_SIZE/2 + 500 - SIL_POINT_SIZE*img.width/2;
		float yShift = SIL_POINT_SIZE/2 + 500 - SIL_POINT_SIZE*img.height/2;

		for(int j = 0; j < img.height; j++)
			for(int i = 0; i < img.width; i++) {
				float x = i*SIL_POINT_SIZE + xShift;
				float y = j*SIL_POINT_SIZE + yShift;

				int color = img.pixels[pixel++];

				// предполагаем greyscale в исходнике, поэтому смотрит только один канал
				int r = (color >> 16) & 0xFF;

				float weight = (float)r/255.0f;

				if(weight < 0.05f) // не добавляем слабозначимые точки
					continue;

				this.silPoints.add(new SilhouettePoint(new PVector(x, y), weight));
			}

		calculateAttractionAreas();

		// элемент интерфейса, который бабблы должны обтекать
		this.uiElements.add(new Rect(this.viewport.min.x, this.viewport.max.y - 200, this.viewport.min.x + 300, this.viewport.max.y));
	}

	public void resetBubbles() {
		this.pins.clear();
		this.bubbles.clear();

		for(int i = 0; i < this.NUM_BUBBLES; i++) {
			int silPointId = (int)(Math.random()*silPoints.size());

			SilhouettePoint point = silPoints.get(silPointId);

			float x = point.rect.min.x + point.rect.getWidth()*(float)Math.random();
			float y = point.rect.min.y + point.rect.getHeight()*(float)Math.random();

			addBubbleWithRandomDim(new PVector(x, y));
		}

		// моделирование сносок на наушниках, нижняя сноска амбушюры с двумя пинами
		// addBubbleWithRandomDim(new PVector(450, 200));
		// addBubbleWithRandomDimAndTwoPins(new PVector(270, 650), new PVector(650, 650));

		this.calculationDone = false;
		this.numIterations = 0;
	}
	
	public void draw() {
		this.pApplet.pushMatrix();

		this.pApplet.translate(this.posX, this.posY);

		float xScale = this.areaWidth/this.viewport.getWidth();
		float yScale = this.areaHeight/this.viewport.getHeight();

		this.pApplet.scale(xScale, yScale);

		// рисуем силуэт

		pApplet.rectMode(PApplet.CORNERS);

		for (SilhouettePoint point : this.silPoints) {
			this.pApplet.stroke(0xFFFF9933, 100);

			this.pApplet.fill(pApplet.color(point.weight*100.0f, point.weight*150.0f, point.weight*255.0f));
			pApplet.rect(point.rect.min.x, 
				point.rect.min.y, 
				point.rect.max.x, 
				point.rect.max.y);
		}

		// рисуем пины
		
		for (Pin pin : this.pins) {
			this.pApplet.noStroke();
			this.pApplet.fill(0xFFFFFF00);
			pApplet.circle(pin.pos.x, pin.pos.y, 5.0f);
		}

		// рисуем бабблы

		this.pApplet.strokeWeight(1.0f);

		this.pApplet.stroke(0xFFFFFFFF, 100);
		
		pApplet.rectMode(PApplet.CORNERS);

		for (Bubble bubble : this.bubbles) {
			Rect bubbleRect = bubble.getRect();

			/*
			// для отладки можно рисовать поля бабла, используемые для отслеживания столкновений

			Rect marginSilRect = bubble.getMarginRect(MARGIN_SILHOUETTE_COLLISION);
			Rect marginOtherBubbleRect = bubble.getMarginRect(MARGIN_BUBBLE_COLLISION);

			this.pApplet.stroke(0xFFFFFFAA, 100);
			this.pApplet.fill(bubble.isGlobalresultForce ? 0xFFFFAAFF: 0xFFAAAAFF, 20);

			pApplet.rect(marginSilRect.min.x, marginSilRect.min.y, 
				marginSilRect.max.x, marginSilRect.max.y);

			this.pApplet.stroke(0xFFFFFFAA, 100);
				this.pApplet.fill(0xFFFFFFAA, 20);
	
			pApplet.rect(marginOtherBubbleRect.min.x, marginOtherBubbleRect.min.y, 
			marginOtherBubbleRect.max.x, marginOtherBubbleRect.max.y);
			*/

			// рисуем сам бабл

			this.pApplet.stroke(0xFFFFFFAA, 255);
			this.pApplet.fill(0xFFFFFFAA, 100);

			this.pApplet.rect(bubbleRect.min.x, bubbleRect.min.y, 
				bubbleRect.max.x, bubbleRect.max.y);
			
			// рисуем линию к пинам

			this.pApplet.stroke(0xFFFFFFFF, 100);

			for(Pin pin : bubble.pins)
				pApplet.line(bubble.pos.x, bubble.pos.y, pin.pos.x, pin.pos.y);

			/*
			
			// для отладки можно рисовать точку центра бокса пересечения бабблов

			if(bubble.intersectionCenter != null) {
				this.pApplet.fill(0xFFFFAAAA, 255);

				pApplet.circle(bubble.intersectionCenter.x, bubble.intersectionCenter.y, 5.0f);
			}
			
			*/
		}

		// рисуем области притяжения

		for(AttractionArea area: this.attractionAreas) {
			this.pApplet.noFill();
			this.pApplet.stroke(0xFFAAFFAA, 100.0f);
			this.pApplet.rect(area.rect.min.x, area.rect.min.y, area.rect.max.x, area.rect.max.y);

			this.pApplet.noStroke();
			this.pApplet.fill(0xFFAAFFAA, 255.0f);

			pApplet.circle(area.attractionPoint.x, area.attractionPoint.y, 15.0f);

			this.pApplet.fill(0xFFFFFFFF, 255);

			//this.pApplet.textSize(20);
		//	this.pApplet.text(Routines.floatToStringComfortReading(area.weight), area.rect.max.x - 60, area.rect.max.y - 20);
		}

		// рисуем прямоугольники UI

		for (Rect uiRect : this.uiElements) {
			this.pApplet.stroke(0xFFFFFFFF, 255);
			this.pApplet.fill(0xFFFFFFFF, 100);

			pApplet.rect(uiRect.min.x, uiRect.min.y, uiRect.max.x, uiRect.max.y);
		}

		// рисуем вьюпорт

		this.pApplet.stroke(0xFFFFFFFF, 255);
		this.pApplet.noFill();

		this.pApplet.rect(viewport.min.x, viewport.min.y, viewport.max.x, viewport.max.y);

		// рисуем тексты

		this.pApplet.fill(0xFFFFFFFF, 255);

		this.pApplet.textSize(20);
		this.pApplet.text("Silhouette points: " + Integer.toString(this.silPoints.size()), 20, 25);
		this.pApplet.text("Bubbles: " + Integer.toString(this.bubbles.size()), 20, 50);
		this.pApplet.text("Iterations: " + Integer.toString(this.numIterations), 20, 75);

		this.pApplet.popMatrix();
	}

	public PVector calcSilhouetteForce(Bubble bubble) {	
		Rect silColRect = bubble.getMarginRect(MARGIN_SILHOUETTE_COLLISION);

		float silColRectSquare = silColRect.getSquare();

		PVector resultForce = new PVector(0.0f, 0.0f);

		boolean hasSilhouetteIntersection = false;
		float silCoveredPercentage = 0.0f;

		for(SilhouettePoint point : this.silPoints) {
			Rect intersectionRect = silColRect.getIntersection(point.rect);

			if(intersectionRect.isEmpty()) // выталкивающая сила действует только от перекрытых бабблом точек
				continue;

			hasSilhouetteIntersection = true;

			PVector intersectionCenter = intersectionRect.getCenter();

			PVector dir = PVector.sub(bubble.pos, intersectionCenter); // направление действия силы - между бабблом и центром пересечения

			// сила выталкивания пропорциональна площади перекрытия бабблом точки силуета и весу точки
			float weightedSquare = intersectionRect.getSquare()*point.weight;

			silCoveredPercentage += weightedSquare;

			dir.normalize();
			dir.mult(weightedSquare);

			resultForce.add(dir);
		}

		if(silCoveredPercentage/silColRectSquare < 0.9f) { // если процент перекрытия силуета бабблом меньше, то используем градиентную силу, посчитанную в цикле выше
			resultForce.normalize();
			resultForce.mult(this.FORCE_FACTOR_SILHOUTTE);
		} else if(hasSilhouetteIntersection){ // иначе, если баббл лежит на сплошной области силуета, и непонятно куда его двигать, двигаем его к ближайшей точке притяжения
			for(AttractionArea area : this.attractionAreas) {
				if(area.rect.isPointInside(bubble.pos))
				{
					PVector attractionForce = PVector.sub(area.attractionPoint, bubble.pos);

					attractionForce.normalize();

					attractionForce.mult(this.FORCE_FACTOR_ATTRACTION_POINT);

					resultForce = attractionForce;
				}
			}
		}

		return resultForce;
	}

	public PVector calcPinsForce(Bubble bubble) {
		Rect bubbleColRect = bubble.getMarginRect(MARGIN_BUBBLE_COLLISION);

		PVector resultForce = new PVector(0.0f, 0.0f);

		for (Pin pin : this.pins) {				
			PVector delta = PVector.sub(bubble.pos, pin.pos);

			PVector dir = delta;

			dir.normalize();
			
			if(bubbleColRect.isPointInside(pin.pos))
			{
				dir.mult(5.0f); // наезд на бабла на любой (в том числе свой) пин создает сильную отталкивающую силу
				resultForce.add(dir);
			}
			else if(bubble.pins.contains(pin)) {
				dir.mult(-1.0f); // притягивающая сила к собственному пину
				resultForce.add(dir);
			}
		}

		resultForce.normalize();
		resultForce.mult(this.FORCE_FACTOR_PINS);

		return resultForce;
	}

	public PVector calcBubbleLongRangeForce(Bubble bubble) {
		PVector resultForce = new PVector(0.0f, 0.0f);

		for (Bubble otherBubble : this.bubbles) {
			if(otherBubble == bubble)
				continue;

			PVector delta = PVector.sub(bubble.pos, otherBubble.pos);

			float sqrDist = delta.magSq();
			float dist =  (float)Math.sqrt(sqrDist);
			float cubedDist = sqrDist*dist;

			// чтобы было удобно подбирать порядок силы. на данный момент наиболее удачным является 1/sqr(dist)
			float invDist = dist < 0.001f ? 1.0f : 1.0f/dist;
			float invSqrDist = sqrDist < 0.001f ? 1.0f : 1.0f/sqrDist;
			float invCubedDist = cubedDist < 0.001f ? 1.0f : 1.0f/cubedDist;

			PVector dir = delta;

			dir.normalize();

			resultForce.add(PVector.mult(dir, invSqrDist*2000.0f)); // коэф можно изменить
		}

		resultForce.mult(this.FORCE_FACTOR_BUBBLE_LONGRANGE);

		return resultForce;
	}

	public PVector calcBubbleIntersectionForce(Bubble bubble) {
		Rect bubbleColRect = bubble.getMarginRect(MARGIN_BUBBLE_COLLISION);

		PVector resultForce	= new PVector(0.0f, 0.0f);

		for (Bubble otherBubble : this.bubbles) {
			if(otherBubble == bubble)
				continue;

			Rect otherMarginRect = otherBubble.getMarginRect(MARGIN_BUBBLE_COLLISION);

			Rect marginIntersectionRect = bubbleColRect.getIntersection(otherMarginRect);

			if(marginIntersectionRect.isEmpty())
				continue;

			PVector intersectionCenter = marginIntersectionRect.getCenter();

			bubble.intersectionCenter = intersectionCenter;

			PVector delta = PVector.sub(bubble.pos, intersectionCenter);

			PVector dir = delta;

			dir.normalize();

			resultForce.add(dir);
		}

		resultForce.normalize();
		resultForce.mult(this.FORCE_FACTOR_BUBBLE_INTERSECTION);

		return resultForce;
	}

	public PVector calcUIIntersectionForce(Bubble bubble) {
		Rect bubbleColRect = bubble.getMarginRect(MARGIN_BUBBLE_COLLISION);

		PVector resultForce	= new PVector(0.0f, 0.0f);

		for (Rect uiRect : this.uiElements) {
			Rect uiIntersectionRect = bubbleColRect.getIntersection(uiRect);

			if(uiIntersectionRect.isEmpty())
				continue;

			PVector intersectionCenter = uiIntersectionRect.getCenter();

			bubble.intersectionCenter = intersectionCenter;

			PVector delta = PVector.sub(bubble.pos, intersectionCenter);

			PVector dir = delta;

			dir.normalize();

			resultForce.add(dir);
		}

		resultForce.normalize();
		resultForce.mult(this.FORCE_FACTOR_UI_INTERSECTION);

		return resultForce;
	}

	public void process() 	{ // выполняет одну итерацию
		if(this.numIterations > MAX_ITERATIONS-1) {
			calculationDone = true;
			return;
		}
		
		// главный цикл расчёта бабблов
		for (Bubble bubble : this.bubbles) {

			/*

			Силы, действующие на баббл:
			1. Выталкивающая сила с силуета. Если непонятно куда выталкивать, подключаются области притяжения
			2. Отталкивание от элементов UI
			3. Дальнодействующее отталкивание между бабблами. 
				Зависит от расстояния, и используется для красивого расположения,
				добавляет пространство между бабблами, и уменьшает вероятность пересечения линий к пинами
			4. Непосредственное отталкивание бабблов при столкновении
			5. Притяжение к собственным пинами баббла, и отталкивание от пинов, на которые баббл наехал

			*/

			PVector totalForce = new PVector(0.0f, 0.0f);

			totalForce.add(calcSilhouetteForce(bubble));

			totalForce.add(calcUIIntersectionForce(bubble));
			
			if(this.numIterations > this.ITER_START_CALC_BUBBLES_LONGRANGE)
				totalForce.add(calcBubbleLongRangeForce(bubble));

			if(this.numIterations > this.ITER_START_CALC_BUBBLES_INTERSECTION)
				totalForce.add(calcBubbleIntersectionForce(bubble));

			if(this.numIterations > this.ITER_START_CALC_PINS)
				totalForce.add(calcPinsForce(bubble));

			// интегрирование
			bubble.vel.add(totalForce);
			bubble.pos.add(bubble.vel);

			// "трение". позволяет быстрее гасить прошедшие возмущения, и уменьшает колебания
			bubble.vel.mult(0.9f);

			// граничные условия (вьюпорт).

			PVector viewportCollisionShift = bubble.getMarginRect(MARGIN_BUBBLE_COLLISION).getShiftToFit(viewport);

			// сдвигаем при врезании в стенку (нулевой вектор, если врезания нет)
			bubble.pos.add(viewportCollisionShift);

			// тормозим по оси, по которой врезались

			if(Math.abs(viewportCollisionShift.x) > 0.0001)
				bubble.vel.x = 0.0f;

			if(Math.abs(viewportCollisionShift.y) > 0.0001)
				bubble.vel.y = 0.0f;
		}

		this.numIterations++;
	}
}