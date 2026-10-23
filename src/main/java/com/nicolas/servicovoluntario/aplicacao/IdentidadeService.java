package com.nicolas.servicovoluntario.aplicacao;

import java.util.Set;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import com.nicolas.servicovoluntario.dominio.Escopo;
import com.nicolas.servicovoluntario.dominio.Policial;
import com.nicolas.servicovoluntario.persistencia.PolicialRepository;

/** Liga o token do SSO ao cadastro de policiais, pela matrícula, e resolve o escopo de unidade de cada perfil. */
@Service
public class IdentidadeService {

    public static final String PERFIL_CAPTACAO = "RESP_CAPTACAO";

    private final PolicialRepository policiais;

    public IdentidadeService(PolicialRepository policiais) {
        this.policiais = policiais;
    }

    public Policial policial(Jwt jwt) {
        var matricula = jwt.getClaimAsString("matricula");
        if (matricula == null || matricula.isBlank()) {
            throw ApiException.proibido("O token não traz a matrícula do policial");
        }
        return policiais.porMatricula(matricula)
                .orElseThrow(() -> ApiException.proibido("Matrícula %s não cadastrada no SVG".formatted(matricula)));
    }

    /** Sem linha em {@code papel_unidade}, o responsável atua apenas na própria unidade. */
    public Escopo escopo(Jwt jwt, Policial policial, String perfil) {
        return policiais.escopo(jwt.getSubject(), perfil)
                .orElseGet(() -> new Escopo(false, Set.of(policial.unidadeId())));
    }
}
