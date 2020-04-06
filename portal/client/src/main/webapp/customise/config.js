var CONFIG = {
	SERVER : "http://localhost:9090/disk-project-server",
	TITLE : "Neuro DISK",
	COLORS : {
		base : "#5D7BA0", // Background color for Top Banner, and Headers
		link : "#5D7BA0", // Text color for Links
		header : "white", // Text color for Headers		
		ok : "#5D7BA0", // Color indicating form items that are filled out
		error : "#D42041" // Color indicating required form items that are not filled out 
	}
}

CONFIG.HOME = "<p>This portal provides access to the DISK Hypothesis reasoner</p>" +
	"<p> To test a given hypothesis, DISK draws from a library of lines of inquiry.    A line of inquiry captures a possible approach to hypothesis testing, and includes: </p>" +
	"<ol> <li> A hypothesis pattern that specifies what types of hypothesis statements the line of inquiry is designed to test,</li> <li> a data query pattern to retrieve relevant data,</li> <li> a set of computational workflows that specify the steps to analyze the data selected, and</li> <li> a set of meta-workflows that specify how to combine the results of the analyses and generate a revised hypothesis and/or confidence values.</li></ol>" +
	"<div> <p> For example, to test the type of hypothesis that <b>a protein is associated with a certain tumor type</b>, we could create a line of inquiry for proteomic analysis. The line of inquiry would have a query pattern to retrieve proteomic data from samples taken from patients that have that tumor type, a workflow that would use that data to do aproteomics analysis to look for likely proteins that appear in the samples, and a meta-workflow to generate a confidence value based on the amount of data and the type of algorithms used in the analysis</p>";


