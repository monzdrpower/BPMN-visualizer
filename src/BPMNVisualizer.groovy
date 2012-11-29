import groovy.io.FileType;
import groovy.util.Node;

/*
 * adds simple layout data to plain *.bpmn20.xml files 
 * result - new file *.bpmn, activiti-eclipse-plugin  compatible
 * 
 * TODO subprocess, etc
 */

def path = /D:\dir-with-bpmn20.xml-files/

String reg(name) {
	def m = (name =~ '.*\\}(.*)')
	m ? m[0][1] : null
}

int sequenceFlowIndex = 0

int calcSubWidth(Node subprocess) {

	def result = 0;
	subprocess.children().each {
		def name = reg(it.name())
		if(name in ['startEvent', 'endEvent'])
			result += 30
		if(name != 'sequenceFlow')
			result += 100
	}
	result + subprocess.children().size() * 30
}

void process1(process, plane, sequenceFlowIndex, startX) {
	def x = startX + 35
	process.children().each {
		
		def name = reg(it.name())
		if(name in ['sequenceFlow', 'boundaryEvent'])
			return
			
		if(name == 'extensionElements')
			return
			
		if(!it.@name)
			it.@name = it.@id
			
		def width, height, y = 220
		if(name in ['startEvent', 'endEvent']){
			width = height = 35
		} else if(name == 'subProcess'){
			width = calcSubWidth(it)
			height = 300
			y = 200
			// recursive subprocess
			process1(it, plane, sequenceFlowIndex, x)
		} else if(name.endsWith('Gateway') || name == 'intermediateCatchEvent'){
			width = height = 40
		} else {
			width = height = 100
		}
		
		//	<bpmndi:BPMNShape bpmnElement="startevent1" id="BPMNShape_startevent1">
		//	<omgdc:Bounds height="35" width="35" x="220" y="180"></omgdc:Bounds>
		//  </bpmndi:BPMNShape>
		def shape = new Node(plane, 'bpmndi:BPMNShape', [bpmnElement:"${it.@id}", id:"BPMNShape_${it.@id}"])
		def bounds = new Node(shape, 'omgdc:Bounds', [x:x, y:y, height:height, width:width])
		
		x += width + 50
	}
	
	// Timers
	process.children().each{ timer ->
		def elementName = reg(timer.name())
		if(elementName != 'boundaryEvent')
			return
			
		if(elementName == 'extensionElements')
			return

		def attachedToRef = plane.children().find{ ch -> ch.@bpmnElement == timer.@attachedToRef}.children().getAt(0)
		def refX = ((attachedToRef.@x as int) + (attachedToRef.@width as int) / 2 as int ) - 15
		def refY = (attachedToRef.@y as int) - 15
				
//		<bpmndi:BPMNShape bpmnElement="escalationTimer" id="BPMNShape_escalationTimer">
//			<omgdc:Bounds height="30" width="30" x="580" y="200"></omgdc:Bounds>
//		</bpmndi:BPMNShape>
		
		def shape = new Node(plane, 'bpmndi:BPMNShape', [bpmnElement:"${timer.@id}", id:"BPMNShape_${timer.@id}"])
		def bounds = new Node(shape, 'omgdc:Bounds', [x:refX, y:refY, height:30, width:30])

	}
	
	// sequenceFlow only
	process.children().eachWithIndex { flow, idx ->
	
		def elementName = reg(flow.name())
		if(elementName != 'sequenceFlow')
			return
			
		if(elementName == 'extensionElements')
			return
			
		if(!flow.@id){
			sequenceFlowIndex += idx
			flow.@id="sequenceFlow$sequenceFlowIndex"
		}
	
		def fromShape = plane.children().find{ ch -> ch.@bpmnElement == flow.@sourceRef}.children().getAt(0)
		def fromX = (fromShape.@x as int) + (fromShape.@width as int)
		def fromY = (fromShape.@y as int) + (fromShape.@height as int) / 2 as int
				
		def toShape = plane.children().find{ ch -> ch.@bpmnElement == flow.@targetRef}.children().getAt(0)
		def toX = (toShape.@x as int)
		def toY = (toShape.@y as int) + (toShape.@height as int) / 2 as int
		
	//      <bpmndi:BPMNEdge bpmnElement="flow1" id="BPMNEdge_flow1">
	//        <omgdi:waypoint x="255" y="197"></omgdi:waypoint>
	//        <omgdi:waypoint x="650" y="197"></omgdi:waypoint>
	//      </bpmndi:BPMNEdge>
		def edge = new Node(plane, 'bpmndi:BPMNEdge', [bpmnElement:"${flow.@id}", id:"BPMNEdge_${flow.@id}"])
		new Node(edge , 'omgdi:waypoint', [x:fromX, y:fromY])
		new Node(edge , 'omgdi:waypoint', [x:toX, y:toY])
	
	}
}


def process = { xml ->

	def definitions = new XmlParser().parseText(xml)
	
	def attr = definitions.attributes()
	attr << ['xmlns:xsi':"http://www.w3.org/2001/XMLSchema-instance"]
	attr << ['xmlns:activiti':"http://activiti.org/bpmn"]
	attr << ['xmlns:bpmndi':"http://www.omg.org/spec/BPMN/20100524/DI"]
	attr << ['xmlns:omgdc':"http://www.omg.org/spec/DD/20100524/DC"]
	attr << ['xmlns:omgdi':"http://www.omg.org/spec/DD/20100524/DI"]
	attr << ['typeLanguage':"http://www.w3.org/2001/XMLSchema"]
	attr << ['expressionLanguage':"http://www.w3.org/1999/XPath"]
	
	//<bpmndi:BPMNDiagram id="BPMNDiagram_process1">
	def diagram = new Node(definitions, 'bpmndi:BPMNDiagram', [id:"BPMNDiagram_${definitions.process[0].@id}"])
	
	// <bpmndi:BPMNPlane bpmnElement="process1" id="BPMNPlane_process1">
	def plane = new Node(diagram, 'bpmndi:BPMNPlane', [bpmnElement:"${definitions.process[0].@id}", id:"BPMNPlane_${definitions.process[0].@id}"])

	process1(definitions.process[0], plane, sequenceFlowIndex, 0)
		
	def writer = new StringWriter()
	new XmlNodePrinter(new PrintWriter(writer)).print(definitions)
	'<?xml version="1.0" encoding="UTF-8"?>\n'+writer.toString()

}

new File(path).eachFileRecurse(FileType.FILES){ file->
	
	if(!file.name.endsWith('bpmn20.xml'))
		return
		
	println file.name
	
	def xml = file.getText()
	
	def newXml = process(xml)
	newXml = newXml.replaceAll('&lt;', '<')
	newXml = newXml.replaceAll('&gt;', '>')
	newXml = newXml.replaceAll('&quot;', '"')
	newXml = newXml.replaceAll(/<activiti:expression>\s*<html>/, /<activiti:expression><![CDATA[<html>/)
	newXml = newXml.replaceAll('</html>\\s*</activiti:expression>', '</html>]]></activiti:expression>')
	
	newXml = newXml.replaceAll('<timeDuration>\\s*\n+\\s*','<timeDuration>')
	newXml = newXml.replaceAll('\\s*\n+\\s*</timeDuration>','</timeDuration>')
	
	def newFile = new File(file.parent.toString() + System.properties.get('file.separator') + file.name.replace('bpmn20.xml', 'bpmn'))
		
	newFile.write(newXml)
}
