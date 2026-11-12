package dev.cannonlines;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public final class AutoReconnectScreen extends Screen {
    private final AutoReconnectManager manager;

    public AutoReconnectScreen(AutoReconnectManager manager) {
        super(Text.literal("CannonLines • Auto-Reconnect"));
        this.manager = manager;
    }

    @Override
    protected void init() {
        int center = this.width / 2;
        int top = this.height / 2;
        this.addDrawableChild(ButtonWidget.builder(
            Text.literal(manager.isEnabled() ? "Disable Auto-Reconnect" : "Enable Auto-Reconnect"),
            button -> {
                manager.toggle(this.client);
                this.client.setScreen(new AutoReconnectScreen(manager));
            }
        ).dimensions(center - 105, top - 8, 210, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Close"), button -> this.close())
            .dimensions(center - 105, top + 20, 210, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        this.renderBackground(context, mouseX, mouseY, deltaTicks);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, this.height / 2 - 65, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer,
            Text.literal("Status: " + manager.getStatus()), this.width / 2, this.height / 2 - 42, 0xD8D8D8);
        context.drawCenteredTextWithShadow(this.textRenderer,
            Text.literal("Server: " + manager.getServerName()), this.width / 2, this.height / 2 - 29, 0xAAAAAA);
        context.drawCenteredTextWithShadow(this.textRenderer,
            Text.literal("Reconnect attempts: " + manager.getAttempts()), this.width / 2, this.height / 2 + 49, 0xAAAAAA);
        super.render(context, mouseX, mouseY, deltaTicks);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
