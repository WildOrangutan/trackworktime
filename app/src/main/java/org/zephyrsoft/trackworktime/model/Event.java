/*
 * This file is part of TrackWorkTime (TWT).
 * 
 * TWT is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * TWT is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with TWT. If not, see <http://www.gnu.org/licenses/>.
 */
package org.zephyrsoft.trackworktime.model;

import org.zephyrsoft.trackworktime.database.DAO;

/**
 * Data class for an event.
 * 
 * @see DAO
 * @author Mathis Dirksen-Thedens
 */
public class Event extends Base implements Comparable<Event> {
	private Integer id = null;
	private Integer week = null;
	private Integer task = null;
	private Integer type = null;
	private String time = null;
	private String text = null;

	public Event() {
		// do nothing
	}

	public Event(Integer id, Integer week, Integer task, Integer type, String time, String text) {
		this.id = id;
		this.week = week;
		this.task = task;
		this.type = type;
		this.time = time;
		this.text = text;
	}

	public Integer getId() {
		return id;
	}

	public Integer getWeek() {
		return week;
	}

	public Integer getTask() {
		return task;
	}

	public Integer getType() {
		return type;
	}

	public String getTime() {
		return time;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public void setWeek(Integer week) {
		this.week = week;
	}

	public void setTask(Integer task) {
		this.task = task;
	}

	public void setType(Integer type) {
		this.type = type;
	}

	public void setTime(String time) {
		this.time = time;
	}

	public String getText() {
		return text;
	}

	public void setText(String text) {
		this.text = text;
	}

	@Override
	public int compareTo(Event another) {
		return compare(getTime(), another.getTime(), compare(getId(), another.getId(), 0));
	}

	/**
	 * This is used e.g. by an ArrayAdapter in a ListView and it is also useful for debugging.
	 * 
	 * @see org.zephyrsoft.trackworktime.model.Base#toString()
	 */
	@Override
	public String toString() {
		return getTime() + " / " + TypeEnum.byValue(getType()).name() + " / " + getTask() + " - " + getText();
	}

}
