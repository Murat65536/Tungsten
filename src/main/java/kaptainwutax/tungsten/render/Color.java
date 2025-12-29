package kaptainwutax.tungsten.render;

public record Color(int red, int green, int blue) {

	public static final Color WHITE = new Color(255, 255, 255);
	public static final Color RED = new Color(255, 0, 0);
	public static final Color GREEN = new Color(0, 255, 0);
	public static final Color BLUE = new Color(0, 0, 255);


	public float getFRed() {
		return this.red() / 255.0F;
	}

	public float getFGreen() {
		return this.green() / 255.0F;
	}

	public float getFBlue() {
		return this.blue() / 255.0F;
	}

}
