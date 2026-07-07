package pl.pixeloza.mc_ai_recorder.client.inference;

public record AiAction(
        long timestamp,
        Buttons buttons,
        Camera camera
) {
    public record Buttons(
            boolean forward,
            boolean back,
            boolean left,
            boolean right,
            boolean jump,
            boolean sneak,
            boolean sprinting,
            boolean attack,
            boolean use,

            // Jednorazowa akcja otwarcia lub zamknięcia EQ.
            boolean inventory
    ) {
    }

    public record Camera(
            float yawDelta,
            float pitchDelta
    ) {
    }
}