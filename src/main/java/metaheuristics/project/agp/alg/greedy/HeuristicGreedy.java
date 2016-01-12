package metaheuristics.project.agp.alg.greedy;

import java.io.File;
import java.io.IOException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.io.FileUtils;

import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Triangle;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryCollection;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineSegment;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.triangulate.ConformingDelaunayTriangulationBuilder;

import metaheuristics.project.agp.alg.Algorithm;
import metaheuristics.project.agp.alg.greedy.heuristics.A6;
import metaheuristics.project.agp.alg.greedy.heuristics.A7;
import metaheuristics.project.agp.alg.greedy.heuristics.Heuristic;
import metaheuristics.project.agp.instances.GalleryInstance;
import metaheuristics.project.agp.instances.components.Camera;
import metaheuristics.project.agp.instances.util.BenchmarkFileInstanceLoader;

public class HeuristicGreedy implements Algorithm{

	public enum InitialSet {
		VERTEX_COVER,
		SIDES_EXTENSION,
		VISIBILITY_EXTENSION,
		TRIANGULATION_COVER
	}
	
	public static double EPSILON = 0.01;

	private GeometryFactory gf = new GeometryFactory();
	
	private Polygon main;
	
	private Heuristic h;
	private InitialSet is;
	
	private HashMap<Camera, Polygon> visPolygons;
	
	HashMap<Camera, Polygon> cover;

	public HeuristicGreedy(InitialSet is, Heuristic h) {
		this.is = is;
		this.h = h;
	}
	
	@Override
	public void process(GalleryInstance gi) {
		this.main = createPolygon(gi.getVertices());
		this.visPolygons = new HashMap<>();
		List<Camera> init = createInitialSet(gi);
		for(int i = 0; i < init.size(); ++i) {
			List<Coordinate> bound = init.get(i).visibilityPolygon(gi).getVertices();
			visPolygons.put(init.get(i), 
					createPolygon(bound));
		}
		cover = new HashMap<>();
		boolean covered = false;
		while(!covered) {
			Camera c = findBest();
			if(c != null) {
				cover.put(c, visPolygons.get(c));
				visPolygons.remove(c);
				covered = checkIfCovered();
				System.out.println("camera in cover");
			}
		}
 	}
	
	private boolean checkIfCovered() {
		double areaAll = main.getArea();
		Polygon[] polygons = cover.values().toArray(new Polygon[cover.values().size()]);
		GeometryCollection polygonCollection = gf.createGeometryCollection(polygons);
		Geometry union = polygonCollection.buffer(0);
		try {
			if(main.difference(union).getArea() < areaAll * EPSILON) return true;
		}catch(Exception e) {
			return false;
		}
		return false;
	}

	private Camera findBest() {
		double maxmi = -1;
		Camera max = null;
		for(Camera c : visPolygons.keySet()) {
			double mi = h.utilValue(visPolygons.get(c), cover, gf);
			if(maxmi == -1 || mi > maxmi) {
				maxmi = mi;
				max = c;
			}
		}
		return max;
	}

	Polygon createPolygon(List<Coordinate> bound) {
		Coordinate[] boundary = new Coordinate[bound.size() + 1];
		for(int i = 0; i < boundary.length - 1; ++i) boundary[i] = bound.get(i);
		boundary[boundary.length - 1] = bound.get(0);
		return gf.createPolygon(boundary);
	}

	private List<Camera> createInitialSet(GalleryInstance gi) {
		switch (is) {
		case VERTEX_COVER:
			return createInitialVertexCover(gi);
		case TRIANGULATION_COVER:
			return createInitialTriangCover(gi);
		default:
			return null;
		}
	}

	private List<Camera> createInitialTriangCover(GalleryInstance gi) {
		List<Camera> ini = new ArrayList<>();
		ConformingDelaunayTriangulationBuilder cdtb =
				new ConformingDelaunayTriangulationBuilder();
		cdtb.setSites(main);
		GeometryCollection gc = (GeometryCollection)cdtb.getTriangles(gf);
		for(int i = 0; i < gc.getNumGeometries(); ++i) {
			Polygon p = (Polygon)gc.getGeometryN(i);
			Coordinate[] verts = p.getCoordinates();
			Triangle t = new Triangle(verts[0], verts[1], verts[2]);
			Coordinate centr = t.centroid();
			if(gf.createPoint(centr).within(main)) {
				ini.add(new Camera(centr.x, centr.y));
				System.out.println("new camera added");
			}
		}
		return ini;
	}

	private List<Camera> createInitialVertexCover(GalleryInstance gi) {
		List<Camera> ini = new ArrayList<Camera>();
		addCandidates(gi.getVertices(), ini);
		return ini;
	}

	private void addCandidates(List<Coordinate> vertices, List<Camera> ini) {
		int size = vertices.size();
		for(int i = 0; i < size; ++i) {
			LineSegment side1 = new LineSegment(vertices.get((i +1) % size), vertices.get(i % size));
			LineSegment side2 = new LineSegment(vertices.get((i + 1) % size), vertices.get((i + 2) % size));
			ini.add(calcInside(vertices, side1.p0, calcBisector(side1, side2)));
		}
		
	}

	private Camera calcInside(List<Coordinate> vertices, Coordinate v, Coordinate bisector) {
		double norm = Math.sqrt(bisector.x * bisector.x + bisector.y * bisector.y);
		Coordinate vbNormalized = new Coordinate(bisector.x / norm, bisector.y / norm);
		Coordinate cand1 = new Coordinate(v.x + vbNormalized.x * EPSILON, v.y + vbNormalized.y * EPSILON);
		Coordinate cand2 = new Coordinate(v.x - vbNormalized.x * EPSILON, v.y - vbNormalized.y * EPSILON);
		Point p1 = gf.createPoint(cand1);
		if(p1.within(main)) return new Camera(cand1.x, cand1.y);
		return new Camera(cand2.x, cand2.y);
	}

	private Coordinate calcBisector(LineSegment side1, LineSegment side2) {
		Coordinate v1 = new Coordinate(side1.p1.x - side1.p0.x, side1.p1.y - side1.p0.y);
		Coordinate v2 = new Coordinate(side2.p1.x - side2.p0.x, side2.p1.y - side2.p0.y);
		Coordinate vb = new Coordinate((v1.x + v2.x) / 2, (v1.y + v2.y) / 2);
		//LineSegment bis = new LineSegment(side1.p0, new Coordinate(side1.p0.x + vb.x, side1.p0.y + vb.y));
		return vb;
	}
	
	public void saveResults(String fname) {
		File file = new File(fname);
		StringBuilder sb = new StringBuilder();
		for(Camera c : cover.keySet()) {
			sb.append(c.toString() + " ");
		}
		try {
			FileUtils.writeStringToFile(file, sb.toString());
		} catch (IOException ignorable) {}
	}
	
	public static void main(String[] args) {
		HeuristicGreedy hg = new HeuristicGreedy(InitialSet.VERTEX_COVER, new A7());
		GalleryInstance gi = new BenchmarkFileInstanceLoader().load("randsimple-60-6.pol");
		hg.process(gi);
		hg.saveResults("camera60-6.sabmple");
		System.out.println(hg.cover.size());
	}
}