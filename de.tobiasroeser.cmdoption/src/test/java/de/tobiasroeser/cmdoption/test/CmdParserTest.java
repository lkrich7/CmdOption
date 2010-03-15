package de.tobiasroeser.cmdoption.test;

import static org.testng.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;

import org.testng.annotations.Test;

import de.tobiasroeser.cmdoption.AddToCollectionHandler;
import de.tobiasroeser.cmdoption.CmdOption;
import de.tobiasroeser.cmdoption.CmdOptionsParser;

public class CmdParserTest {

	public static class Config1 {
		@CmdOption(args = { "PAR" })
		public String name1;
		@CmdOption
		public String name2 = "name-2";
		@CmdOption
		public boolean option1 = false;
		@CmdOption
		public boolean option2 = true;
		@CmdOption
		public Boolean option3 = false;
		@CmdOption
		public Boolean option4 = true;
		@CmdOption
		public boolean option5 = false;
		@CmdOption
		public Boolean option6 = false;

		@CmdOption(args = "PAR", maxCount = -1, handler = AddToCollectionHandler.class)
		public Collection<String> addTo1 = new LinkedList<String>();

		public boolean fieldForMethodOpt1;

		@CmdOption
		public void methodOpt1() {
			this.fieldForMethodOpt1 = true;
		}

		public String fieldForMethodOpt2;

		@CmdOption(args = "PAR")
		public void methodOpt2(String par1) {
			this.fieldForMethodOpt2 = par1;
		}
	}

	@Test
	public void testParse1() {
		CmdOptionsParser parser = new CmdOptionsParser(Config1.class, false, true);
		Config1 config = new Config1();
		parser.parseCmdline(Arrays.asList("--name1", "name-1", "--option5",
				"--option6"), config);
		assertEquals(config.name1, "name-1");
		assertEquals(config.name2, "name-2");
		assertEquals(config.option1, false);
		assertEquals(config.option2, true);
		assertEquals(config.option3, (Boolean) false);
		assertEquals(config.option4, (Boolean) true);
		assertEquals(config.option5, true);
		assertEquals(config.option6, (Boolean) true);

		parser.parseCmdline(Arrays.asList("--addTo1", "added-1", "--addTo1",
				"added-2"), config);
		assertEquals(config.addTo1, Arrays.asList("added-1", "added-2"));

		parser.parseCmdline(Arrays.asList("--methodOpt1"), config);
		assertEquals(config.fieldForMethodOpt1, true);

		parser.parseCmdline(Arrays.asList("--methodOpt2", "par1-set"), config);
		assertEquals(config.fieldForMethodOpt2, "par1-set");

	}

	@Test
	public void testPrintHelp() {
		CmdOptionsParser parser = new CmdOptionsParser(Config1.class, false, true);
		String output1 = parser.formatOptions();
		System.out.println(output1);
	}
}
