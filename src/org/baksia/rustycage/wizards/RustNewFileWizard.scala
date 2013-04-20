package org.baksia.rustycage.wizards

import org.baksia.rustycage.RustPlugin
import org.eclipse.core.resources._
import org.eclipse.core.runtime._
import org.eclipse.jface.operation.IRunnableWithProgress
import org.eclipse.jface.viewers.ISelection
import org.eclipse.jface.viewers.IStructuredSelection
import org.eclipse.jface.wizard.Wizard
import org.eclipse.ui._
import org.eclipse.ui.ide.IDE

import java.io.{ByteArrayInputStream, InputStream}
import java.lang.reflect.InvocationTargetException

class RustNewFileWizard extends Wizard with INewWizard {
  setNeedsProgressMonitor(true)

  override def addPages() {
    pageFile = new RustNewFileWizardPage(selection)
    addPage(pageFile)
  }

  override def init(workbench: IWorkbench, selection: IStructuredSelection) {
    RustNewFileWizard.this.selection = selection
  }

  override def performFinish(): Boolean = {
    val containerName = pageFile.getContainerName
    val fileName = pageFile.getFileName
    def op = new IRunnableWithProgress() {
      def run(monitor:IProgressMonitor ) {
        try {
          doFinish(containerName, fileName, monitor)
        } catch {
          case e: CoreException => throw new InvocationTargetException(e)
        } finally {
          monitor.done()
        }
      }
    }
    try {
      getContainer.run(true, false, op)
    } catch {
      case _: Exception =>
        false
    }
    true
  }


  def doFinish(containerName: String, fileName: String, monitor: IProgressMonitor) {
    monitor.beginTask("Creating " + fileName, 2)
    val root: IWorkspaceRoot = ResourcesPlugin.getWorkspace.getRoot

    val container: IContainer = root.findMember(new Path(containerName)).asInstanceOf[IContainer]
   
    if (!container.exists()) {
      throwCoreException("Container \"" + containerName + "\" does not exist.")
    }
    val file = container.getFile(new Path(fileName))
    val preferenceStore = RustPlugin.prefStore
    val projectName = preferenceStore.getString("ProjectName")
    val crateFile = container.getFile(new Path(projectName + ".rc"))
    val stream: InputStream = openContentStream()
    val crateStream: InputStream = openCrateContentStream(fileName)
    if (file.exists()) {
      file.setContents(stream, true, true, monitor)
    } else {
      file.create(stream, true, monitor)
    }
    if (crateFile.exists()) {
      crateFile.appendContents(crateStream, true, true, monitor)
    }
    crateStream.close()
    stream.close()

    monitor.worked(1)
    monitor.setTaskName("Opening file for editing...")
    getShell.getDisplay.asyncExec(new Runnable() {
      def run() {
        val page: IWorkbenchPage = PlatformUI.getWorkbench.getActiveWorkbenchWindow.getActivePage
        IDE.openEditor(page, file, true)
      }
    })
    monitor.worked(1)
  }

  private def openCrateContentStream(fileName: String): InputStream = {
    val modName = fileName.substring(0, fileName.length() - 3)
    val contents = "\nmod " + modName + ";"
    new ByteArrayInputStream(contents.getBytes)
  }

  private def openContentStream(): InputStream = {
    val contents = "/*This file is generated with RustyCage*/"
    new ByteArrayInputStream(contents.getBytes)
  }

  private def throwCoreException(message: String) =  {
    val status =
      new Status(IStatus.ERROR, "RustyCage", IStatus.OK, message, null)
    throw new CoreException(status)
  }

  private var pageFile: RustNewFileWizardPage = _

  private var selection: ISelection = _
}