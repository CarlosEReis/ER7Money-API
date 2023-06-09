package com.carloser7.er7money.api.repository.lancamento;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.util.ObjectUtils;

import com.carloser7.er7money.api.dto.LancamentoEstatisticaCategoria;
import com.carloser7.er7money.api.dto.LancamentoEstatisticaDia;
import com.carloser7.er7money.api.dto.LancamentoEstatisticaPessoa;
import com.carloser7.er7money.api.model.Categoria_;
import com.carloser7.er7money.api.model.Lancamento;
import com.carloser7.er7money.api.model.Lancamento_;
import com.carloser7.er7money.api.model.Pessoa_;
import com.carloser7.er7money.api.projection.ResumoLancamento;
import com.carloser7.er7money.api.repository.filter.LancamentoFilter;

public class LancamentoRepositoryImpl implements LancamentoRepositoryQuery{

	@PersistenceContext
	private EntityManager manager;
	
	@Override
	public Page<Lancamento> filtrar(LancamentoFilter lancamentoFilter, Pageable pageable) {
		CriteriaBuilder builder = this.manager.getCriteriaBuilder();
		CriteriaQuery<Lancamento> criteria = builder.createQuery(Lancamento.class);	
		Root<Lancamento> root = criteria.from(Lancamento.class);
		
		Predicate[] predicates = criarRestricoes(lancamentoFilter, builder, root);
		criteria.where(predicates);
		TypedQuery<Lancamento> query = this.manager.createQuery(criteria);
		adicionaRestricoesDePaginacao(query, pageable);
		
		return new PageImpl<>(query.getResultList(), pageable, total(lancamentoFilter)) ;
	}
	
	@Override
	public Page<ResumoLancamento> resumir(LancamentoFilter lancamentoFilter, Pageable pageable) {
		CriteriaBuilder builder = manager.getCriteriaBuilder();
		CriteriaQuery<ResumoLancamento> criteria = builder.createQuery(ResumoLancamento.class);
		Root<Lancamento> root = criteria.from(Lancamento.class);
		
		criteria.select(builder.construct(ResumoLancamento.class, 
			root.get(Lancamento_.codigo),
			root.get(Lancamento_.descricao),
			root.get(Lancamento_.dataVencimento),
			root.get(Lancamento_.dataPagamento),
			root.get(Lancamento_.valor),
			root.get(Lancamento_.tipo),
			root.get(Lancamento_.categoria).get(Categoria_.nome),
			root.get(Lancamento_.pessoa).get(Pessoa_.nome)));
		
		Predicate[] predicates = criarRestricoes(lancamentoFilter, builder, root);
		criteria.where(predicates);
		
		TypedQuery<ResumoLancamento> query = this.manager.createQuery(criteria);
		adicionaRestricoesDePaginacao(query, pageable);
		return new PageImpl<>(query.getResultList(), pageable, total(lancamentoFilter)) ;
	}

	private Predicate[] criarRestricoes(LancamentoFilter lancamentoFilter, CriteriaBuilder builder,
			Root<Lancamento> root) {
		
		List<Predicate> predicates = new ArrayList<>();
		
		if (!ObjectUtils.isEmpty(lancamentoFilter.getDescricao())) {
			predicates.add(
				builder.like(
					builder.lower(root.get(Lancamento_.descricao)), 
					"%"+ lancamentoFilter.getDescricao().toLowerCase() +"%"
				)
			);
		}
		
		if (lancamentoFilter.getDataVencimentoDe() != null) {
			predicates.add(
				builder.greaterThanOrEqualTo(
					root.get(Lancamento_.dataVencimento),
					lancamentoFilter.getDataVencimentoDe()
				)	
			);
		}
		
		if (lancamentoFilter.getDataVencimentoAte() != null) {
			predicates.add(
				builder.lessThanOrEqualTo(
					root.get(Lancamento_.dataVencimento),
					lancamentoFilter.getDataVencimentoAte())
			);
		}
		
		return predicates.toArray(new Predicate[predicates.size()]);
	}

	private void adicionaRestricoesDePaginacao(TypedQuery<?> query, Pageable pageable) {
		int paginaAtual = pageable.getPageNumber();
		int totalRegistrosPorPagina = pageable.getPageSize();
		int primeiroRegistroDaPagina = paginaAtual * totalRegistrosPorPagina;
		
		query.setFirstResult(primeiroRegistroDaPagina);
		query.setMaxResults(totalRegistrosPorPagina);
	}

	private Long total(LancamentoFilter lancamentoFilter) {
		CriteriaBuilder builder = manager.getCriteriaBuilder();
		CriteriaQuery<Long> criteria = builder.createQuery(Long.class);
		Root<Lancamento> root = criteria.from(Lancamento.class);
		
		Predicate[] predicates = criarRestricoes(lancamentoFilter, builder, root);
		criteria.where(predicates);
		
		criteria.select(builder.count(root));
		return manager.createQuery(criteria).getSingleResult();
	}

	@Override
	public List<LancamentoEstatisticaCategoria> porCategoria(LocalDate mesReferencia) {
		CriteriaBuilder builder = this.manager.getCriteriaBuilder();
		CriteriaQuery<LancamentoEstatisticaCategoria> criteria = builder.createQuery(LancamentoEstatisticaCategoria.class);
		Root<Lancamento> root = criteria.from(Lancamento.class);
		
		criteria.select(
			builder.construct(
				LancamentoEstatisticaCategoria.class,
				root.get(Lancamento_.categoria),
				builder.sum(root.get(Lancamento_.valor))
			)
		);
		
		LocalDate primeiroDia = mesReferencia.withDayOfMonth(1);
		LocalDate ultimoDia = mesReferencia.withDayOfMonth(mesReferencia.lengthOfMonth());
		
		criteria.where(
			builder.greaterThanOrEqualTo(root.get(Lancamento_.DATA_VENCIMENTO), primeiroDia),
			builder.lessThanOrEqualTo(root.get(Lancamento_.DATA_VENCIMENTO), ultimoDia)
		);
		
		criteria.groupBy(root.get(Lancamento_.categoria));
		TypedQuery<LancamentoEstatisticaCategoria> createQuery = manager.createQuery(criteria);
		return createQuery.getResultList();
	}

	@Override
	public List<LancamentoEstatisticaDia> porDia(LocalDate mesReferencia) {
		CriteriaBuilder builder = this.manager.getCriteriaBuilder();
		CriteriaQuery<LancamentoEstatisticaDia> criteria = builder.createQuery(LancamentoEstatisticaDia.class);
		Root<Lancamento> root = criteria.from(Lancamento.class);
		
		criteria.select(
			builder.construct(
				LancamentoEstatisticaDia.class,
				root.get(Lancamento_.TIPO),
				root.get(Lancamento_.DATA_VENCIMENTO),
				builder.sum(root.get(Lancamento_.VALOR))
			)
		);
		
		LocalDate primeiroDia = mesReferencia.withDayOfMonth(1);
		LocalDate ultimoDia = mesReferencia.withDayOfMonth(mesReferencia.lengthOfMonth());
		
		criteria.where(
			builder.greaterThanOrEqualTo(root.get(Lancamento_.DATA_VENCIMENTO), primeiroDia),
			builder.lessThanOrEqualTo(root.get(Lancamento_.DATA_VENCIMENTO), ultimoDia)
		);
		
		criteria.groupBy(
			root.get(Lancamento_.TIPO),
			root.get(Lancamento_.DATA_VENCIMENTO)
		);
		TypedQuery<LancamentoEstatisticaDia> createQuery = manager.createQuery(criteria);
		return createQuery.getResultList();
	}
	
	@Override
	public List<LancamentoEstatisticaPessoa> porPessoa(LocalDate inicio, LocalDate fim) {
		CriteriaBuilder builder = this.manager.getCriteriaBuilder();
		CriteriaQuery<LancamentoEstatisticaPessoa> criteria = builder.createQuery(LancamentoEstatisticaPessoa.class);
		Root<Lancamento> root = criteria.from(Lancamento.class);
		
		criteria.select(
			builder.construct(
				LancamentoEstatisticaPessoa.class,
				root.get(Lancamento_.TIPO),
				root.get(Lancamento_.PESSOA),
				builder.sum(root.get(Lancamento_.VALOR)))
		).where(
			builder.greaterThanOrEqualTo(root.get(Lancamento_.DATA_VENCIMENTO), inicio),
			builder.lessThanOrEqualTo(root.get(Lancamento_.DATA_VENCIMENTO), fim)
		).groupBy(
			root.get(Lancamento_.TIPO),
			root.get(Lancamento_.PESSOA)
		).orderBy(
			builder.asc(root.get(Lancamento_.PESSOA)),
			builder.asc(root.get(Lancamento_.TIPO))
		);
		
		TypedQuery<LancamentoEstatisticaPessoa> createQuery = manager.createQuery(criteria);
		return createQuery.getResultList();
	}
}
