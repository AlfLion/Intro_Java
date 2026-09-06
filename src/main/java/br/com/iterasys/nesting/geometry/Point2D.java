package br.com.iterasys.nesting.geometry;

public final class Point2D {

    public final double x;
    public final double y;

    public Point2D(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public Point2D plus(Point2D o) {
        return new Point2D(x + o.x, y + o.y);
    }

    public Point2D scale(double s) {
        return new Point2D(x * s, y * s);
    }

    public Point2D rotateDeg(double angleDeg, Point2D pivot) {
        double rad = Math.toRadians(angleDeg);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        double dx = x - pivot.x;
        double dy = y - pivot.y;
        return new Point2D(pivot.x + dx * cos - dy * sin, pivot.y + dx * sin + dy * cos);
    }

    public double distanceTo(Point2D o) {
        return Math.hypot(x - o.x, y - o.y);
    }
}
