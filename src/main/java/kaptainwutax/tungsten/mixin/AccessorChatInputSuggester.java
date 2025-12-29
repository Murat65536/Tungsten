package kaptainwutax.tungsten.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.gui.screen.ChatInputSuggestor;
import net.minecraft.client.gui.widget.TextFieldWidget;

@Mixin(ChatInputSuggestor.class)
public interface AccessorChatInputSuggester {
	 @Accessor
    TextFieldWidget getTextField();
}
