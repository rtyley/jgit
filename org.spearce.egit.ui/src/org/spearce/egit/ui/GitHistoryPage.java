/*
 *  Copyright (C) 2006  Robin Rosenberg
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License, version 2.1, as published by the Free Software Foundation.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301
 */
package org.spearce.egit.ui;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;

import org.eclipse.compare.CompareConfiguration;
import org.eclipse.compare.structuremergeviewer.ICompareInput;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.viewers.ILazyContentProvider;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableLayout;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.events.MouseMoveListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.team.core.RepositoryProvider;
import org.eclipse.team.core.history.IFileRevision;
import org.eclipse.team.internal.ui.TeamUIMessages;
import org.eclipse.team.internal.ui.history.DialogHistoryPageSite;
import org.eclipse.team.ui.history.HistoryPage;
import org.eclipse.team.ui.history.IHistoryCompareAdapter;
import org.eclipse.team.ui.history.IHistoryPageSite;
import org.spearce.egit.core.GitProvider;
import org.spearce.egit.core.internal.mapping.GitCommitFileRevision;
import org.spearce.egit.core.internal.mapping.GitFileHistory;
import org.spearce.egit.core.internal.mapping.GitFileHistoryProvider;
import org.spearce.egit.core.internal.mapping.GitFileRevision;
import org.spearce.egit.core.project.RepositoryMapping;
import org.spearce.egit.ui.internal.actions.GitCompareRevisionAction;
import org.spearce.jgit.lib.ObjectId;
import org.spearce.jgit.lib.Tag;
import org.spearce.jgit.lib.TopologicalSorter;
import org.spearce.jgit.lib.Repository.StGitPatch;
import org.spearce.jgit.lib.TopologicalSorter.Edge;
import org.spearce.jgit.lib.TopologicalSorter.Lane;

public class GitHistoryPage extends HistoryPage implements IAdaptable,
		IHistoryCompareAdapter {

	private static final String PREF_SHOWALLREPOVERSIONS = "org.spearce.egit.ui.githistorypage.showallrepoversions";
	private static final String PREF_SHOWALLPROJECTVERSIONS = "org.spearce.egit.ui.githistorypage.showallprojectversions";
	private static final String PREF_SHOWALLFOLDERVERSIONS = "org.spearce.egit.ui.githistorypage.showallfolderversions";

	private Composite localComposite;

	private TableViewer viewer;

	private Table table;

	private List<IFileRevision> fileRevisions;

	protected boolean hintShowDiffNow;

	private boolean showAllProjectVersions;

	private boolean showAllFolderVersions;

	private boolean showAllRepoVersions;

	public GitHistoryPage(Object object) {
		setInput(object);
		showAllProjectVersions = Activator.getDefault().getPreferenceStore()
				.getBoolean(PREF_SHOWALLPROJECTVERSIONS);
		showAllFolderVersions = Activator.getDefault().getPreferenceStore()
				.getBoolean(PREF_SHOWALLFOLDERVERSIONS);
		showAllRepoVersions = Activator.getDefault().getPreferenceStore()
				.getBoolean(PREF_SHOWALLREPOVERSIONS);
	}

	public boolean inputSet() {
		if (viewer != null)
			viewer.setInput(getInput());
		// TODO Auto-generated method stub
		return true;
	}

	public void createControl(Composite parent) {
		localComposite = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout();
		layout.marginHeight = 0;
		layout.marginWidth = 0;
		localComposite.setLayout(layout);
		GridData data = new GridData(SWT.FILL, SWT.FILL, true, true);
		data.grabExcessVerticalSpace = true;
		localComposite.setLayoutData(data);

		createTable(localComposite);

		IHistoryPageSite parentSite = getHistoryPageSite();
		if (parentSite != null && parentSite instanceof DialogHistoryPageSite)
			parentSite.setSelectionProvider(viewer);

		final GitCompareRevisionAction compareAction = new GitCompareRevisionAction(
				"Compare");
		final GitCompareRevisionAction compareActionPrev = new GitCompareRevisionAction(
				"Show commit");
		table.addMouseListener(new MouseListener() {
		
			public void mouseUp(MouseEvent e) {
			}
		
			public void mouseDown(MouseEvent e) {
				hintShowDiffNow = e.button==1;
			}
		
			public void mouseDoubleClick(MouseEvent e) {
			}
		
		});

		table.addMouseMoveListener(new MouseMoveListener() {
			TableItem lastItem;
			public void mouseMove(MouseEvent e) {
				TableItem item = table.getItem(new Point(e.x,e.y));
				if (item != null && item!=lastItem) {
					IFileRevision rev = (IFileRevision) item.getData();
					if (rev == null)
						return;
					String commitStr=null;
					if (appliedPatches!=null) {
						String id = rev.getContentIdentifier();
						if (!id.equals("Workspace") && !id.equals("Index")) {
							StGitPatch patch = (StGitPatch) appliedPatches.get(new ObjectId(id));
							if (patch!=null)
								commitStr = "Patch: "+patch.getName();
						} else {
							commitStr = id.toString();
						}
					}
					if (commitStr == null)
						commitStr = "Commit: "+rev.getContentIdentifier();
					table.setToolTipText(commitStr+"\nAuthor: "+rev.getAuthor()+"\nDate: "+new Date(rev.getTimestamp())+"\n\n"+rev.getComment());
				}
				lastItem = item;
			}
		});

		table.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				// update the current
				TableItem[] selection = table.getSelection();
				IFileRevision[] selection2 = new IFileRevision[selection.length];
				for (int i = 0; i < selection.length; ++i) {
					selection2[i] = (IFileRevision) selection[i].getData();
				}

				compareAction.setCurrentFileRevision(fileRevisions.get(0));
				compareAction.selectionChanged(new StructuredSelection(
						selection2));
				if (selection2.length == 1 && hintShowDiffNow) {
					if (selection2[0] instanceof GitCommitFileRevision) {
						ObjectId[] parentIds = ((GitCommitFileRevision)selection2[0]).getCommit().getParentIds();
						if (parentIds.length > 0) {
							ObjectId parentId = parentIds[0];
							IFileRevision previous = new GitCommitFileRevision(parentId,
									((GitFileRevision)selection2[0]).getResource(),
									((GitFileRevision)selection2[0]).getCount()+1);
							compareActionPrev.setCurrentFileRevision(null);
							compareActionPrev.selectionChanged(new StructuredSelection(new IFileRevision[] {selection2[0], previous}));
							System.out.println("detail="+e.detail);
							table.getDisplay().asyncExec(new Runnable() {
								public void run() {
									if (GitCompareRevisionAction.findReusableCompareEditor(GitHistoryPage.this.getSite().getPage()) != null)
										compareActionPrev.run();
								}
							});
						}
					}
				} else {
					compareActionPrev.setCurrentFileRevision(null);
					compareActionPrev.selectionChanged(new StructuredSelection(new IFileRevision[0]));
				}
				hintShowDiffNow = false;
			}
		});
		compareAction.setPage(this);
		compareActionPrev.setPage(this);
		MenuManager menuMgr = new MenuManager();
		Menu menu = menuMgr.createContextMenu(table);
		menuMgr.addMenuListener(new IMenuListener() {
			public void menuAboutToShow(IMenuManager menuMgr) {
				menuMgr.add(compareAction);
				menuMgr.add(compareActionPrev);
			}
		});
		menuMgr.setRemoveAllWhenShown(true);
		table.setMenu(menu);

		GitHistoryResourceListener resourceListener = new GitHistoryResourceListener();
		ResourcesPlugin.getWorkspace().addResourceChangeListener(
				resourceListener, IResourceChangeEvent.POST_CHANGE);

		Action showAllRepoVersionsAction = new Action("R") {
			public void run() {
				setShowAllRepoVersions(isChecked());
				if (historyRefreshJob.cancel()) {
					System.out.println("rescheduling");
					historyRefreshJob.schedule();
				} else {
					System.out.println("failed to cancel?");
				}
			}
		};
		showAllRepoVersionsAction
				.setToolTipText("Show all versions for the repository containing the resource");
		showAllRepoVersionsAction.setChecked(isShowAllRepoVersions());
		getSite().getActionBars().getToolBarManager()
				.add(showAllRepoVersionsAction);

		Action showAllProjectVersionsAction = new Action("P") {
			public void run() {
				setShowAllProjectVersions(isChecked());
				if (historyRefreshJob.cancel()) {
					System.out.println("rescheduling");
					historyRefreshJob.schedule();
				} else {
					System.out.println("failed to cancel?");
				}
			}
		};
		showAllProjectVersionsAction
				.setToolTipText("Show all versions for the project containing the resource");
		showAllProjectVersionsAction.setChecked(isShowAllProjectVersions());
		getSite().getActionBars().getToolBarManager()
				.add(showAllProjectVersionsAction);

		Action showAllFolderVersionsAction = new Action("F") {
			public void run() {
				setShowAllFolderVersion(isChecked());
				if (historyRefreshJob.cancel()) {
					System.out.println("rescheduling");
					historyRefreshJob.schedule();
				} else {
					System.out.println("failed to cancel?");
				}
			}
		};
		showAllFolderVersionsAction
				.setToolTipText("Show all versions for the folder containing the resource");
		showAllFolderVersionsAction.setChecked(isShowAllFolderVersions());
		getSite().getActionBars().getToolBarManager().add(
				showAllFolderVersionsAction);
	}

	private boolean isShowAllRepoVersions() {
		return showAllRepoVersions;
	}

	protected void setShowAllRepoVersions(boolean showAllRepoVersions) {
		this.showAllRepoVersions = showAllRepoVersions;
		Activator.getDefault().getPreferenceStore().setValue(
				PREF_SHOWALLREPOVERSIONS, showAllRepoVersions);
	}

	private boolean isShowAllProjectVersions() {
		return showAllProjectVersions;
	}

	protected void setShowAllProjectVersions(boolean showAllProjectVersions) {
		this.showAllProjectVersions = showAllProjectVersions;
		Activator.getDefault().getPreferenceStore().setValue(
				PREF_SHOWALLPROJECTVERSIONS, showAllProjectVersions);
	}

	private boolean isShowAllFolderVersions() {
		return showAllFolderVersions;
	}

	protected void setShowAllFolderVersion(boolean showAllFolderVersions) {
		this.showAllFolderVersions = showAllFolderVersions;
		Activator.getDefault().getPreferenceStore().setValue(
				PREF_SHOWALLFOLDERVERSIONS, showAllFolderVersions);
	}

	class GitHistoryResourceListener implements IResourceChangeListener {

		public void resourceChanged(IResourceChangeEvent event) {
			System.out.println("resourceChanged(" + event + ")");
		}

	}

	class GitHistoryLabelProvider {

		public String getColumnText(int index, int columnIndex) {
			GitFileRevision element = (GitFileRevision) fileRevisions.get(index);
			if (columnIndex == 0) {
				return "";
			}
			
			if (columnIndex == 1) {
				String rss = element.getURI().toString();
				String rs = rss.substring(rss.length()-10);
				String id = element.getContentIdentifier();
				if (appliedPatches!=null) {
					if (!id.equals("Workspace") && !id.equals("Index")) {
						StGitPatch patch = (StGitPatch) appliedPatches.get(new ObjectId(id));
						if (patch!=null)
							return patch.getName();
					}
				}
				if (id != null)
					if (id.length() > 9) // make sure "Workspace" is spelled out
						return id.substring(0, 7) + "..";
					else
						return id;
				else
					return id + "@.." + rs;
			}

			if (columnIndex == 2) {
				String id = element.getContentIdentifier();
				if (id.equals("Workspace")) {
					return "";
				}
				if (id.equals("Index")) {
					return "";
				}
				ObjectId oid = new ObjectId(id);
				StringBuilder b=new StringBuilder();
				if (tags != null) {
					Tag[] matching = tags.get(oid);
					if (matching != null) {
						for (Tag t : matching) {
							if (b.length() > 0)
								b.append(' ');
							String tag = t.getTag();
							b.append(tag);
						}
					}
				}
				if (branches != null) {
					if (b.length() >0)
						b.append('\n');
					String[] matching = branches.get(oid);
					if (matching != null) {
						for (String t : matching) {
							if (b.length() > 0)
								b.append(' ');
							if (t.startsWith("refs/heads/"))
								t = t.substring(11);
							else if (t.startsWith("refs/remotes/"))
								t = t.substring(13);
							b.append(t);
						}
					}
				}
				return b.toString();
			}

			if (columnIndex == 3) {
				Date d = new Date(element.getTimestamp());
				if (d.getTime() == -1)
					return "";
				return d.toString();
			}
			if (columnIndex == 4)
				return element.getAuthor();

			if (columnIndex == 5) {
				String comment = element.getComment();
				if (comment == null)
					return null;
				int p = comment.indexOf('\n');
				if (p >= 0)
					return comment.substring(0, p);
				else
					return comment;
			}
			return Integer.toString(columnIndex);
		}
	}

	private void createTable(Composite composite) {
		table = new Table(composite, SWT.H_SCROLL | SWT.V_SCROLL | SWT.MULTI
				| SWT.FULL_SELECTION | SWT.VIRTUAL);
		table.setHeaderVisible(true);
		table.setLinesVisible(true);
		GridData data = new GridData(SWT.FILL, SWT.FILL, true, true);
		table.setLayoutData(data);
		table.setData("HEAD");
		table.addListener(SWT.SetData, new Listener() {
			public void handleEvent(Event event) {
				try {
//					System.out.println("handleEvent "+event.type+" "+event.index + "=> "+fileRevisions.get(event.index));
					TableItem item = (TableItem) event.item;
					Table parent = item.getParent();
					if (parent == null) {
						item.setText(new String[] { "hej", "san" });
						item.setData("");
					} else {
						for (int i = 0; i < 6; ++i) {
							String text = lp.getColumnText(event.index, i);
							if (text != null)
								item.setText(i, text);
							else
								item.setText(i, "");
						}
						GitFileRevision revision = (GitFileRevision) fileRevisions.get(event.index);
						item.setData(revision);
						if (revision.getCount() >= 0)
							item.setForeground(Display.getCurrent().getSystemColor(SWT.COLOR_BLACK));
						else
							item.setForeground(Display.getCurrent().getSystemColor(SWT.COLOR_DARK_GRAY));
					}
					item.setFont(0,JFaceResources.getBannerFont());
				} catch (Throwable b) {
					b.printStackTrace();
				}
			}
		});

		table.addListener(SWT.PaintItem, new Listener() {

			final int[] colors = {
				SWT.COLOR_BLACK,
				SWT.COLOR_BLUE,
				SWT.COLOR_CYAN,
				SWT.COLOR_DARK_BLUE,
				SWT.COLOR_DARK_CYAN,
				SWT.COLOR_DARK_GREEN,
				SWT.COLOR_DARK_MAGENTA,
				SWT.COLOR_DARK_RED,
				SWT.COLOR_DARK_YELLOW,
				SWT.COLOR_GRAY,
				SWT.COLOR_GREEN,
				SWT.COLOR_MAGENTA,
				SWT.COLOR_RED,
//				SWT.COLOR_WHITE,
				SWT.COLOR_YELLOW
			};
			public void handleEvent(Event event) {
				TableItem item = (TableItem) event.item;
				if (event.index == 0) {
					System.out.println(event);
					if (!(item.getData() instanceof GitCommitFileRevision))
						return;
					GitCommitFileRevision element = (GitCommitFileRevision)item.getData();
					ObjectId xx = element.getCommitId();
					final int DOTRADIUS = 3;
					final int INTERLANE = 8;
					final int LINEWIDTH = 2;
					int x = event.x + INTERLANE/2 + 2;
					int y = event.y;
					int h = event.height;
					event.gc.setLineWidth(LINEWIDTH);

					Lane lane = element.getLane();
					TopologicalSorter<ObjectId> counter = lane.getSorter();
					Integer io = counter.getInternalPosition(xx);
					List<Edge<ObjectId>> px = counter.getEdgeFrom(xx);
					for (TopologicalSorter<ObjectId>.Lane li : counter.currentLanes) {
						Integer iost = counter.getInternalPosition(li.startsAt);
						Integer ioen = counter.getInternalPosition(li.endsAt);
						// Middle of lane
						//	 o
						//	 |
						//	 o
						if (iost != null && io > iost && (ioen == null || io < ioen)) {
							event.gc.setForeground(Display.getCurrent().getSystemColor(colors[li.getNumber()%colors.length]));
							event.gc.drawLine(x + li.getNumber()*INTERLANE, y, x + li.getNumber()*INTERLANE, y + h);
						}
						if (li == lane) {
							if (iost != null && io > iost && (ioen == null || io <= ioen)) {
								event.gc.setForeground(Display.getCurrent().getSystemColor(colors[li.getNumber()%colors.length]));
								event.gc.drawLine(x + li.getNumber()*INTERLANE, y, x + li.getNumber()*INTERLANE, y + h/2);
							}
							if (iost != null && io >= iost && (ioen == null || io < ioen)) {
								event.gc.setForeground(Display.getCurrent().getSystemColor(colors[li.getNumber()%colors.length]));
								event.gc.drawLine(x + li.getNumber()*INTERLANE, y + h/2, x + li.getNumber()*INTERLANE, y + h);
							}
						}
						// branch out to parent
						//   o						 o
						//   \>>>>>>>>>>o o<<<<<<<<<</
						if (px != null) {
							for (int j=0; j<px.size(); ++j) {
								ObjectId p = px.get(j).getTo();
								Lane pl = counter.getLane(p);
								if (li == pl && lane != li) {
									int fromn = lane.getNumber();
									int ton = pl.getNumber();
									int x1 = x + fromn * INTERLANE;
									int x2 = x + ton * INTERLANE;
									event.gc.setForeground(Display.getCurrent().getSystemColor(colors[pl.getNumber()%colors.length]));
									if (x1 < x2)
										event.gc.drawArc(x1 - (x2-x1), y+h/2, (x2-x1)*2, h, 0, 90);
									else
										event.gc.drawArc(x2, y+h/2, (x1-x2)*2, h, 90, 90);
								}
							}
						}
						// merge, SAME (unless we get really smart)
					}
					// Draw dot
					if (xx.equals(currentHead)) {
						event.gc.setBackground(Display.getCurrent().getSystemColor(SWT.COLOR_RED));
						event.gc.fillOval((int) (x + lane.getNumber() *  INTERLANE - LINEWIDTH/2.0+0.5 - DOTRADIUS*3.0/2), y + h/2 - DOTRADIUS*3/2, DOTRADIUS*3, DOTRADIUS*3);
					} else
						event.gc.setBackground(Display.getCurrent().getSystemColor(SWT.COLOR_BLACK));
						event.gc.fillOval((int) (x + lane.getNumber() *  INTERLANE - LINEWIDTH/2.0+0.5 - DOTRADIUS), y + h/2 - DOTRADIUS, DOTRADIUS*2, DOTRADIUS*2);
				}
			}
		});
		TableLayout layout = new TableLayout();
		table.setLayout(layout);

		viewer = new TableViewer(table, SWT.VIRTUAL | SWT.FULL_SELECTION);

		viewer.setUseHashlookup(true);

		createColumns();

		viewer.setContentProvider(new GitHistoryContentProvider());

		viewer.setInput(getInput());
	}

	private Map appliedPatches;
	ObjectId currentHead;
	private Map<ObjectId,Tag[]> tags;
	private Map<ObjectId, String[]> branches;
	GitHistoryLabelProvider lp = new GitHistoryLabelProvider();

	class HistoryRefreshJob extends Job {

		public HistoryRefreshJob(String name) {
			super(name);
		}

		protected IStatus run(IProgressMonitor monitor) {
			try {
				System.out.println("Job started @"+new Date());
				monitor.beginTask("UpdateHistory", IProgressMonitor.UNKNOWN);
				IProject project = ((IResource) getInput()).getProject();
				RepositoryProvider provider = RepositoryProvider
						.getProvider(project);
				RepositoryMapping repositoryMapping = RepositoryMapping.getMapping(project);
				Map newappliedPatches = null;
				try {
					newappliedPatches = repositoryMapping.getRepository().getAppliedPatches();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				Map<ObjectId,Tag[]> newtags = new HashMap<ObjectId,Tag[]>();
				try {
					for (String name : repositoryMapping.getRepository().getTags()) {
						Tag t = repositoryMapping.getRepository().mapTag(name);
						Tag[] samecommit = newtags.get(t.getObjId());
						if (samecommit==null) { 
							samecommit = new Tag[] { t };
						} else {
							Tag[] n=new Tag[samecommit.length+1];
							for (int j=0; j<samecommit.length; ++j)
								n[j] = samecommit[j];
							n[n.length-1] = t;
							samecommit = n;
						}
						newtags.put(t.getObjId(), samecommit);
					}
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				try {
					currentHead = repositoryMapping.getRepository().resolve("HEAD");
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				Map<ObjectId, String[]> newBranches = new HashMap<ObjectId, String[]>();
				try {
					for (String branch : repositoryMapping.getRepository().getBranches()) {
						ObjectId id = repositoryMapping.getRepository().resolve(branch);
						String[] samecommit = newBranches.get(id);
						if (samecommit == null) {
							samecommit = new String[] { branch };
						} else {
							String[] n=new String[samecommit.length + 1];
							for (int j=0; j<samecommit.length; ++j)
								n[j] = samecommit[j];
							n[n.length-1] = branch;
							samecommit = n;
						}
						newBranches.put(id, samecommit);
					}
					String[] samecommit = newBranches.get(currentHead);
					if (samecommit == null) {
						samecommit = new String[] { "HEAD" };
					} else {
						String[] n=new String[samecommit.length + 1];
						for (int j=0; j<samecommit.length; ++j)
							n[j] = samecommit[j];
						n[n.length-1] = "HEAD";
						samecommit = n;
					}
					newBranches.put(currentHead, samecommit);
					branches = newBranches;
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				GitFileHistoryProvider fileHistoryProvider = (GitFileHistoryProvider) provider
						.getFileHistoryProvider();
				IResource startingPoint = (IResource) getInput();
				if (isShowAllFolderVersions())
					if (!(startingPoint instanceof IContainer))
						startingPoint = startingPoint.getParent();
				if (isShowAllProjectVersions())
					startingPoint = startingPoint.getProject();

				GitFileHistory fileHistoryFor = fileHistoryProvider
						.getHistoryFor(startingPoint,
								-1,
								monitor,
								isShowAllRepoVersions());
				fileRevisions = fileHistoryFor.getFileRevisionsList();
				final Map fnewappliedPatches = newappliedPatches;
				final Map<ObjectId,Tag[]> ftags = newtags;
	
				table.getDisplay().asyncExec(new Runnable() {
				
					public void run() {
						table.removeAll();
						table.setItemCount(fileRevisions.size());
						table.setData("X");
						table.setLayoutData(new GridData(SWT.FILL,SWT.FILL,true,true));
						System.out.println("inputchanged, invoking refresh");
						appliedPatches = fnewappliedPatches;
						tags = ftags;
						long t0 = System.currentTimeMillis();
						viewer.refresh();
						long t1 = System.currentTimeMillis();
						System.out.println("refresh in "+(t1-t0)/1000.0+"s");
						done(Status.OK_STATUS);
						System.out.println("History refresh at "+new Date());
					}
				
				});
				done(Status.OK_STATUS);

			} catch (CancellationException e) {
				System.out.println("Job cancelled");
				done(Status.CANCEL_STATUS);
				return Status.CANCEL_STATUS;

			} finally {
				if (getState() == Job.RUNNING) {
					System.out.println("Job aborted for unknow reason");
					done(Status.CANCEL_STATUS);
				}
		  	}
			return Status.OK_STATUS;
		}
	}

	HistoryRefreshJob historyRefreshJob = new HistoryRefreshJob("Git history refresh");
	
	class GitHistoryContentProvider implements ILazyContentProvider {

		public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
			if (newInput == null)
				return;
			if (newInput == oldInput)
				return;
			IProject project = ((IResource) getInput()).getProject();
			RepositoryProvider provider = RepositoryProvider.getProvider(project);
			if (!(provider instanceof GitProvider))
				return;

			System.out.println(new Date()+"inputChanged(" + viewer + "," + oldInput + ","
					+ newInput);
			if (historyRefreshJob.cancel()) {
				System.out.println("rescheduling");
				historyRefreshJob.schedule();
			} else {
				System.out.println("failed to cancel?");
			}
		}

		public void dispose() {
		}

		public void updateElement(int index) {
//			viewer.update(arg0, arg1)
		}
	}

	private void createColumns() {
		// X SelectionListener headerListener = getColumnListener(viewer);
		// count from head
		TableColumn col = new TableColumn(table, SWT.NONE);
		col.setResizable(true);
		col.setText("^");
		// X col.addSelectionListener(headerListener);
		((TableLayout) table.getLayout()).addColumnData(new ColumnWeightData(15,
				true));

		// revision
		col = new TableColumn(table, SWT.NONE);
		col.setResizable(true);
		col.setText(TeamUIMessages.GenericHistoryTableProvider_Revision);
		// X col.addSelectionListener(headerListener);
		((TableLayout) table.getLayout()).addColumnData(new ColumnWeightData(15,
				true));

		// tags
		col = new TableColumn(table, SWT.NONE);
		col.setResizable(true);
		col.setText("Tags");
		// X col.addSelectionListener(headerListener);
		((TableLayout) table.getLayout()).addColumnData(new ColumnWeightData(15,
				true));
		// creation date
		col = new TableColumn(table, SWT.NONE);
		col.setResizable(true);
		col.setText(TeamUIMessages.GenericHistoryTableProvider_RevisionTime);
		// X col.addSelectionListener(headerListener);
		((TableLayout) table.getLayout()).addColumnData(new ColumnWeightData(30,
				true));

		// author
		col = new TableColumn(table, SWT.NONE);
		col.setResizable(true);
		col.setText(TeamUIMessages.GenericHistoryTableProvider_Author);
		// X col.addSelectionListener(headerListener);
		((TableLayout) table.getLayout()).addColumnData(new ColumnWeightData(20,
				true));

		// comment
		col = new TableColumn(table, SWT.NONE);
		col.setResizable(true);
		col.setText(TeamUIMessages.GenericHistoryTableProvider_Comment);
		// X col.addSelectionListener(headerListener);
		((TableLayout) table.getLayout()).addColumnData(new ColumnWeightData(35,
				true));
	}

	public Control getControl() {
		return localComposite;
	}

	public void setFocus() {
		localComposite.setFocus();
	}

	public String getDescription() {
		return "GIT History viewer";
	}

	public String getName() {
		return getInput().toString();
	}

	public boolean isValidInput(Object object) {
		// TODO Auto-generated method stub
		return true;
	}

	public void refresh() {
		// TODO Auto-generated method stub

	}

	public Object getAdapter(Class adapter) {
		if (adapter == IHistoryCompareAdapter.class) {
			return this;
		}
		return null;
	}

	public ICompareInput getCompareInput(Object object) {
		// TODO Auto-generated method stub
		return null;
	}

	public void prepareInput(ICompareInput input,
			CompareConfiguration configuration, IProgressMonitor monitor) {
		System.out.println("prepareInput()");
		// TODO Auto-generated method stub
	}

}
