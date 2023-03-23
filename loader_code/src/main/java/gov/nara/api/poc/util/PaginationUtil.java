package gov.nara.api.poc.util;

import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PaginationUtil {
	private final Logger logger = LoggerFactory.getLogger(PaginationUtil.class);

	private ReentrantLock lock;

	private Integer totalBOsCount;
	
	private Integer batchSize = 0;

	private boolean isSkip = false;
	
	private int status;
	
	private int pageSize;
	
	private int pageIndex;
	
	public PaginationUtil() {
		lock = new ReentrantLock();
	}

//	public boolean decreaseTotalBOsCount(Integer size) {
//		lock.lock();
//		boolean result = false;
//		try {
//			totalBOsCount = totalBOsCount - size;
//			if (totalBOsCount.equals(0)) {
//				result = true;
//			}
//		} finally {
//			lock.unlock();
//		}
//		return result;
//	}

	public Integer getTotalBOsCount() {
		return totalBOsCount;
	}

	public void setTotalBOsCount(Integer totalBOsCount) {
		this.totalBOsCount = totalBOsCount;
	}
	
	public void setBatchSize(int batchSize) {
		this.batchSize = batchSize;
	}

	public boolean decreaseBatchSize() {
		lock.lock();
		boolean result = false;
		try {
			batchSize--;
			if (batchSize.equals(0)) {
				result = true;
			}
		} finally {
			lock.unlock();
		}
		return result;
	}

	public boolean isSkip() {
		return isSkip;
	}

	public void setSkip(boolean isSkip) {
		this.isSkip = isSkip;
	}

	public int getStatus() {
		return status;
	}

	public void setStatus(int status) {
		this.status = status;
	}

	public int getPageSize() {
		return pageSize;
	}

	public void setPageSize(int pageSize) {
		this.pageSize = pageSize;
	}

	public int getPageIndex() {
		return pageIndex;
	}

	public void setPageIndex(int pageIndex) {
		this.pageIndex = pageIndex;
	}
}
