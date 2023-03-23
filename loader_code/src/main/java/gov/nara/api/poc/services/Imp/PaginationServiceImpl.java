package gov.nara.api.poc.services.Imp;

import java.util.List;

import javax.jms.Queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.jms.JmsException;
import org.springframework.jms.core.JmsMessagingTemplate;
import org.springframework.stereotype.Service;

import gov.nara.api.poc.pesistance.BOMig;
import gov.nara.api.poc.pesistance.BOMigRepo;
import gov.nara.api.poc.util.PaginationUtil;

@Service
public class PaginationServiceImpl {

	private final Logger logger = LoggerFactory.getLogger(PaginationServiceImpl.class);

	@Autowired
	private JmsMessagingTemplate jmsMessagingTemplate;

	@Autowired
	private Queue migQueue;

	@Autowired
	private Queue errorQueue;

	@Autowired
	private PaginationUtil paginationUtil;

	@Autowired
	private BOMigRepo bOMigRepo;

	@Autowired
	private DataServiceImp dataService;

	public void getNextBOPageBystatus() {

		int totalRequiredPages = paginationUtil.getPageSize() * (paginationUtil.getPageIndex() + 1);

		int totalSize = bOMigRepo.countByMigStatus(paginationUtil.getStatus());

		if (totalSize > totalRequiredPages) {
			paginationUtil.setTotalBOsCount(totalRequiredPages);
		} else {
			paginationUtil.setTotalBOsCount(totalSize);
		}

		List<BOMig> page = bOMigRepo.findByMigStatus(paginationUtil.getStatus(),
				PageRequest.of(0, paginationUtil.getPageSize()));
		paginationUtil.setPageIndex(paginationUtil.getPageIndex() - 1);
		paginationUtil.setBatchSize(page.size());

		if (paginationUtil.getPageIndex() == -1) {
			paginationUtil.setTotalBOsCount(0);
		}

		if (page != null && page.size() > 0) {
			for (BOMig bOMig : page) {
				try {
					if (paginationUtil.getStatus() == -1) {
						jmsMessagingTemplate.convertAndSend(errorQueue, bOMig.getBoId());
					} else if (paginationUtil.getStatus() == 0) {
						jmsMessagingTemplate.convertAndSend(migQueue,
								dataService.getProcessDataFromDB(bOMig.getBoId()).toString());
					}

				} catch (JmsException e) {
					logger.error(e.getMessage());
				}
			}
		}
	}
}
