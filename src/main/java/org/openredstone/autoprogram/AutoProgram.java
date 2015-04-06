package org.openredstone.autoprogram;

import java.io.File;
import java.util.Scanner;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;

public class AutoProgram extends JavaPlugin implements Listener
{
	public final Logger logger = Logger.getLogger("Minecraft");
	public final PluginDescriptionFile descFile = this.getDescription();
	private final String progPath = this.getConfig().getString("progPath");
	
	public void onEnable()
	{
		this.saveDefaultConfig();
		logger.info(descFile.getName()+" enabled.");

		File file = new File(progPath);
		logger.info(descFile.getName()+"'s path is: "+file.getAbsolutePath());
	}

	public void onDisable()
	{
		logger.info(descFile.getName()+" disabled.");
	}

	public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args)
	{
		if (cmd.getName().equalsIgnoreCase("autoprogram"))
		{
			return onCommandAutoprogram(sender, args);
		}
		return false;
	}

	private boolean onCommandAutoprogram(CommandSender sender, String[] args)
	{
		if (!(sender instanceof Player))
		{
			sender.sendMessage("This command can only be run by a player.");
			return false;
		}

		Player player = (Player)sender;

		//Autoprogram requires exactly one argument.
		if (args.length != 1)
		{
			sender.sendMessage("Usage: autoprogram <name>");
			return false;
		}

		//Can't allow bad characters in program names.
		if (!Pattern.compile("^[a-zA-Z0-9_]+$").matcher(args[0]).find())
		{
			player.sendMessage("The program name contains illegal characters.");
			return false;
		}

		try
		{
			//We strip out all dashes because the web API gives us the UUID with no dashes,
			//and this will have to interface with software which uses the web API
			//to get UUIDs
			String uuid = player.getUniqueId().toString().replaceAll("-", "");

			File dir = new File(progPath+"/"+uuid);
			if (!dir.exists())
			{
				logger.info("Player "+player.getName()+" doesn't have a prog file directory. Creating it.");
				dir.mkdir();
			}

			Scanner file = new Scanner(new File(dir, args[0]+".prog"));
			String prog = "";
			while (file.hasNextLine()) prog += file.nextLine()+"\n";
			file.close();

			ProgParser parser = new ProgParser(prog, player);
			parser.exec();
			return true;
		}
		catch (Exception e)
		{
			player.sendMessage(e.getMessage());
			return false;
		}
	}
}
