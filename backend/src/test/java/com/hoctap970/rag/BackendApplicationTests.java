package com.hoctap970.rag;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class BackendApplicationTests {

	@Autowired
	private WebApplicationContext context;

	@Test
	void contextLoads() {
	}

	@Test
	void missingStaticResourceIsNotAServerError() throws Exception {
		MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();
		mvc.perform(get("/does-not-exist")).andExpect(status().isNotFound());
		mvc.perform(get("/favicon.svg")).andExpect(status().isOk());
	}

}
