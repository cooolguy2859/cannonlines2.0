package dev.cannonlines;

import net.minecraft.client.gui.GuiGraphicsExtractor;
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
                this.client.gui.setScreen(new AutoReconnectScreen(manager));
            }
        ).dimensions(center - 105, top - 8, 210, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Close"), button -> this.close())
            .dimensions(center - 105, top + 20, 210, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        this.extractBackground(context, mouseX, mouseY, deltaTicks);
        context.text(this.font, this.title, this.width / 2 - this.font.width(this.title) / 2, this.height / 2 - 65, 0xFFFFFF, true);
        context.text(this.font, "Status: " + manager.getStatus(), this.width / 2 - this.font.width("Status: " + manager.getStatus()) / 2, this.height / 2 - 42, 0xD8D8D8, true);
        context.text(this.font, "Server: " + manager.getServerName(), this.width / 2 - this.font.width("Server: " + manager.getServerName()) / 2, this.height / 2 - 29, 0xAAAAAA, true);
        context.text(this.font, "Reconnect attempts: " + manager.getAttempts(), this.width / 2 - this.font.width("Reconnect attempts: " + manager.getAttempts()) / 2, this.height / 2 + 49, 0xAAAAAA, true);
        super.extractRenderState(context, mouseX, mouseY, deltaTicks);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
